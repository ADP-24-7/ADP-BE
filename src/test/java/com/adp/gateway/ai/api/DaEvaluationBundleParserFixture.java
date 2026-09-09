package com.adp.gateway.ai.api;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import com.adp.gateway.ai.application.AiEvaluationBundleCanonicalizer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.InputFormat;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;

final class DaEvaluationBundleParserFixture {
    private final ObjectMapper objectMapper;
    private final Set<String> requiredSections;
    private final com.networknt.schema.Schema schema;
    private final AiEvaluationBundleCanonicalizer canonicalizer;

    DaEvaluationBundleParserFixture(ObjectMapper objectMapper) throws IOException {
        this.objectMapper = objectMapper;
        this.canonicalizer = new AiEvaluationBundleCanonicalizer(objectMapper);
        String schemaJson = Files.readString(Path.of("docs/contracts/ai-evaluation-bundle.schema.json"));
        JsonNode schemaNode = objectMapper.readTree(schemaJson);
        this.requiredSections = StreamSupport.stream(schemaNode.path("required").spliterator(), false)
            .map(JsonNode::asText)
            .collect(Collectors.toUnmodifiableSet());
        this.schema = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
            .getSchema(schemaJson, InputFormat.JSON);
    }

    ParsedBundle parse(String json) throws IOException {
        var schemaErrors = schema.validate(
            json,
            InputFormat.JSON,
            context -> context.executionConfig(config -> config.formatAssertionsEnabled(true))
        );
        if (!schemaErrors.isEmpty()) {
            throw new IllegalArgumentException("DA bundle failed JSON Schema validation: " + schemaErrors);
        }
        JsonNode root = objectMapper.readTree(json);
        if (!requiredSections.stream().allMatch(root::has)) {
            throw new IllegalArgumentException("DA bundle is missing a required section");
        }
        JsonNode manifest = root.path("manifest");
        if (!Set.of("adp-ai-evaluation-bundle/v1", "adp-ai-evaluation-bundle/v2").contains(manifest.path("schema_version").asText())) {
            throw new IllegalArgumentException("DA bundle schema is unsupported");
        }
        Map<String, Object> content = new TreeMap<>(Map.of(
            "schema_version", manifest.path("schema_version").asText(),
            "execution_config", objectMapper.convertValue(root.path("execution_config"), Object.class),
            "case_results", objectMapper.convertValue(root.path("case_results"), Object.class),
            "runtime_metrics", objectMapper.convertValue(root.path("runtime_metrics"), Object.class),
            "failure_summary", objectMapper.convertValue(root.path("failure_summary"), Object.class),
            "trace_index", objectMapper.convertValue(root.path("trace_index"), Object.class)
        ));
        if (root.has("contract_evidence")) content.put("contract_evidence", root.get("contract_evidence"));
        String calculatedDigest = canonicalizer.digest(content);
        if (!calculatedDigest.equals(manifest.path("content_digest").asText())) {
            throw new IllegalArgumentException("DA bundle content digest is inconsistent");
        }
        int executionCount = manifest.path("execution_count").asInt(-1);
        if (executionCount != root.path("case_results").size()
            || executionCount != root.path("runtime_metrics").size()
            || executionCount != root.path("trace_index").size()) {
            throw new IllegalArgumentException("DA bundle section cardinality is inconsistent");
        }
        Set<String> caseExecutionIds = values(root.path("case_results"), "execution_id");
        Set<String> metricExecutionIds = values(root.path("runtime_metrics"), "execution_id");
        Set<String> traceExecutionIds = values(root.path("trace_index"), "execution_id");
        if (caseExecutionIds.size() != executionCount
            || !caseExecutionIds.equals(metricExecutionIds)
            || !caseExecutionIds.equals(traceExecutionIds)) {
            throw new IllegalArgumentException("DA bundle execution identity is inconsistent");
        }
        if (!caseModelIdentity(root.path("case_results"))
            .equals(caseModelIdentity(root.path("runtime_metrics")))) {
            throw new IllegalArgumentException("DA bundle case-model execution identity is inconsistent");
        }
        int evaluatedExecutionCount = root.path("failure_summary").path("evaluated_execution_count").asInt(-1);
        Set<String> configuredModelIds = values(root.path("execution_config").path("models"), "profile_id");
        Set<String> evaluatedModelIds = values(root.path("case_results"), "model_profile_id");
        if (executionCount != evaluatedExecutionCount
            || configuredModelIds.size() != root.path("execution_config").path("models").size()
            || !configuredModelIds.equals(evaluatedModelIds)
            || manifest.path("model_count").asInt(-1) != configuredModelIds.size()
            || manifest.path("case_count").asInt(-1) != values(root.path("case_results"), "eval_case_id").size()) {
            throw new IllegalArgumentException("DA bundle manifest cardinality is inconsistent");
        }
        Set<String> caseModelPairs = new HashSet<>();
        for (JsonNode result : root.path("case_results")) {
            String pair = result.path("eval_case_id").asText() + "\u0000"
                + result.path("model_profile_id").asText();
            if (!caseModelPairs.add(pair)) {
                throw new IllegalArgumentException("DA bundle case-model identity is duplicated");
            }
            if (!result.path("expected_input_digest").equals(result.path("actual_input_digest"))) {
                throw new IllegalArgumentException("DA bundle input digest is inconsistent");
            }
        }
        Set<String> expectedPairs = new HashSet<>();
        for (String caseId : values(root.path("case_results"), "eval_case_id")) {
            for (String modelId : configuredModelIds) {
                expectedPairs.add(caseId + "\u0000" + modelId);
            }
        }
        if (!expectedPairs.equals(caseModelPairs)) {
            throw new IllegalArgumentException("DA bundle case-model Cartesian product is incomplete");
        }
        validateFailureSummary(root.path("runtime_metrics"), root.path("failure_summary"));
        return new ParsedBundle(
            root.path("execution_config").path("evaluation_run_id").asText(),
            executionCount,
            manifest.path("model_count").asInt(),
            evaluatedExecutionCount
        );
    }

    private void validateFailureSummary(JsonNode metrics, JsonNode summary) {
        int failed = 0;
        int sentUnknown = 0;
        int notAttempted = 0;
        Map<String, Integer> byErrorCategory = new TreeMap<>();
        for (JsonNode metric : metrics) {
            failed += "FAILED".equals(metric.path("provider_status").asText()) ? 1 : 0;
            sentUnknown += "SENT_UNKNOWN".equals(metric.path("provider_status").asText()) ? 1 : 0;
            notAttempted += "NOT_ATTEMPTED".equals(metric.path("measurement_type").asText()) ? 1 : 0;
            String errorCategory = metric.path("error_category").asText();
            if (!"NONE".equals(errorCategory)) {
                byErrorCategory.merge(errorCategory, 1, Integer::sum);
            }
            validateTokenUsage(metric);
        }
        Map<String, Integer> receivedByErrorCategory = new TreeMap<>();
        summary.path("by_error_category").properties().forEach(entry ->
            receivedByErrorCategory.put(entry.getKey(), entry.getValue().asInt())
        );
        if (summary.path("failed").asInt(-1) != failed
            || summary.path("sent_unknown").asInt(-1) != sentUnknown
            || summary.path("not_attempted").asInt(-1) != notAttempted
            || !receivedByErrorCategory.equals(byErrorCategory)) {
            throw new IllegalArgumentException("DA bundle failure summary is inconsistent");
        }
    }

    private void validateTokenUsage(JsonNode metric) {
        boolean complete = "COMPLETE".equals(metric.path("token_usage_status").asText());
        JsonNode input = metric.path("input_tokens");
        JsonNode output = metric.path("output_tokens");
        JsonNode total = metric.path("total_tokens");
        if (complete) {
            if (!input.isIntegralNumber() || !output.isIntegralNumber() || !total.isIntegralNumber()
                || total.asLong() != input.asLong() + output.asLong()) {
                throw new IllegalArgumentException("DA bundle token usage is inconsistent");
            }
        } else if (!input.isNull() || !output.isNull() || !total.isNull()) {
            throw new IllegalArgumentException("DA bundle token usage is inconsistent");
        }
    }

    private Set<String> values(JsonNode array, String field) {
        return StreamSupport.stream(array.spliterator(), false)
            .map(item -> item.path(field).asText())
            .collect(Collectors.toUnmodifiableSet());
    }

    private Map<String, String> caseModelIdentity(JsonNode array) {
        Map<String, String> identities = new LinkedHashMap<>();
        for (JsonNode item : array) {
            String identity = item.path("eval_case_id").asText() + "\u0000"
                + item.path("model_profile_id").asText();
            if (identities.put(item.path("execution_id").asText(), identity) != null) {
                throw new IllegalArgumentException("DA bundle execution identity is duplicated");
            }
        }
        return Map.copyOf(identities);
    }

    record ParsedBundle(
        String evaluationRunId,
        int executionCount,
        int modelCount,
        int evaluatedExecutionCount
    ) {
    }
}
