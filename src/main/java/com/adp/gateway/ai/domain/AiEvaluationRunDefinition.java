package com.adp.gateway.ai.domain;

import java.util.Map;
import java.util.Set;

public record AiEvaluationRunDefinition(
    String evaluationRunId,
    String runVersion,
    String datasetId,
    String datasetVersion,
    String datasetDigest,
    String policySnapshotDigest,
    String contractDigest,
    Map<String, AiEvaluationCaseDefinition> cases,
    Set<String> modelProfileIds
) {
    public AiEvaluationRunDefinition {
        if (isBlank(evaluationRunId) || isBlank(runVersion) || isBlank(datasetId) || isBlank(datasetVersion)
            || !isDigest(datasetDigest) || !isDigest(policySnapshotDigest) || !isDigest(contractDigest)
            || cases == null || cases.isEmpty() || modelProfileIds == null || modelProfileIds.isEmpty()) {
            throw new IllegalArgumentException("Evaluation run provenance is incomplete");
        }
        cases = Map.copyOf(cases);
        modelProfileIds = Set.copyOf(modelProfileIds);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean isDigest(String value) {
        return value != null && value.matches("sha256:[0-9a-f]{64}");
    }
}
