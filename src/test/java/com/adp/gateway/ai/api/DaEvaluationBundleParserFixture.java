package com.adp.gateway.ai.api;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.InputFormat;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;

final class DaEvaluationBundleParserFixture {
    private final ObjectMapper objectMapper;
    private final Set<String> requiredSections;
    private final com.networknt.schema.Schema schema;

    DaEvaluationBundleParserFixture(ObjectMapper objectMapper) throws IOException {
        this.objectMapper = objectMapper;
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
        if (!"adp-ai-evaluation-bundle/v1".equals(manifest.path("schema_version").asText())) {
            throw new IllegalArgumentException("DA bundle schema is unsupported");
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
        return new ParsedBundle(
            root.path("execution_config").path("evaluation_run_id").asText(),
            executionCount,
            manifest.path("model_count").asInt(),
            evaluatedExecutionCount
        );
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
