package com.adp.gateway.ai.domain;

import java.util.Set;

public record AiEvaluationRunDefinition(
    String evaluationRunId,
    String runVersion,
    String datasetId,
    String datasetVersion,
    String datasetDigest,
    String policySnapshotDigest,
    Set<String> evalCaseIds,
    Set<String> modelProfileIds
) {
    public AiEvaluationRunDefinition {
        evalCaseIds = Set.copyOf(evalCaseIds);
        modelProfileIds = Set.copyOf(modelProfileIds);
    }
}
