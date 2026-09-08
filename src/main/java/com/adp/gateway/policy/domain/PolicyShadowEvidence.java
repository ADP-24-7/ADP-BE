package com.adp.gateway.policy.domain;

import java.time.OffsetDateTime;
import java.util.List;

public record PolicyShadowEvidence(
    String shadowEvaluationId,
    String institutionId,
    String workloadId,
    String purposeCode,
    String baselineArtifactId,
    String baselineArtifactVersion,
    String baselineArtifactDigest,
    String candidateArtifactId,
    String candidateArtifactVersion,
    String candidateArtifactDigest,
    long candidateRevision,
    String evaluationCaseId,
    String evaluationCaseVersion,
    String inputDigest,
    String baselineOutcomeDigest,
    String candidateOutcomeDigest,
    List<PolicyShadowDiffField> diffFields,
    String result,
    String evaluatedBy,
    OffsetDateTime evaluatedAt
) {
    public PolicyShadowEvidence {
        diffFields = List.copyOf(diffFields);
    }
}
