package com.adp.gateway.ai.domain;

public record AiEvaluationReference(
    String evaluationRunId,
    String evalCaseId,
    String policySnapshotDigest
) {
}
