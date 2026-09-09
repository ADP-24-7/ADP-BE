package com.adp.gateway.ai.domain;

import java.util.Map;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/** Server-owned, immutable pre-provider snapshot; no retrieved values or credentials. */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AiEvaluationContractSnapshot(JsonNode fixedConditions, String fixedConditionsDigest,
    java.util.List<AiEvaluationBundle.ModelConfig> modelProfiles) {
    public AiEvaluationContractSnapshot(JsonNode fixedConditions, String fixedConditionsDigest) {
        this(fixedConditions, fixedConditionsDigest, java.util.List.of());
    }
    public AiEvaluationContractSnapshot {
        if (fixedConditions == null || !fixedConditions.isObject()
            || fixedConditionsDigest == null || !fixedConditionsDigest.matches("sha256:[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Incomplete fixed conditions");
        }
        for (String field : java.util.List.of("evaluation_run_id", "evaluation_contract_version", "workload",
            "purpose_code", "dataset_version", "dataset_digest", "prompt_version", "policy_version",
            "policy_snapshot_digest", "transform_version", "rag_mode", "rag_version", "seed_control",
            "reasoning_control", "case_set_version")) {
            if (!fixedConditions.hasNonNull(field) || fixedConditions.get(field).asText().isBlank()) {
                throw new IllegalArgumentException("Missing contract field: " + field);
            }
        }
        if (!fixedConditions.path("temperature").isNumber()
            || !Double.isFinite(fixedConditions.path("temperature").asDouble())
            || fixedConditions.path("temperature").asDouble() < 0
            || !fixedConditions.path("max_tokens").isIntegralNumber()
            || fixedConditions.path("max_tokens").asLong() < 1
            || !fixedConditions.path("stream").isBoolean()
            || fixedConditions.path("stream").asBoolean()
            || !"NOT_CONFIGURABLE".equals(fixedConditions.path("seed_control").asText())
            || !"NOT_CONFIGURABLE".equals(fixedConditions.path("reasoning_control").asText())
            || !fixedConditions.path("prompt_snapshot").isObject()
            || !fixedConditions.path("transform_snapshot").isArray()) {
            throw new IllegalArgumentException("Invalid frozen execution controls or artifacts");
        }
        fixedConditions = fixedConditions.deepCopy();
        modelProfiles = java.util.List.copyOf(modelProfiles);
    }

    @Override public JsonNode fixedConditions() { return fixedConditions.deepCopy(); }
}
