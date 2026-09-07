package com.adp.gateway.ai.api;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Files;
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
        return new ParsedBundle(
            root.path("execution_config").path("evaluation_run_id").asText(),
            executionCount,
            manifest.path("model_count").asInt(),
            root.path("failure_summary").path("evaluated_execution_count").asInt()
        );
    }

    record ParsedBundle(
        String evaluationRunId,
        int executionCount,
        int modelCount,
        int evaluatedExecutionCount
    ) {
    }
}
