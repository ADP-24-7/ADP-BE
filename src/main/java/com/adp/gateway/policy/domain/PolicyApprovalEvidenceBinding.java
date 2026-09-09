package com.adp.gateway.policy.domain;

public record PolicyApprovalEvidenceBinding(
    String shadowEvaluationId,
    long candidateRevision,
    String candidateArtifactDigest,
    String baselineArtifactId,
    String baselineArtifactVersion,
    String baselineArtifactDigest,
    String evaluationCaseId,
    String evaluationCaseVersion,
    String shadowResult,
    String approvalPolicyVersion
) {
    public static PolicyApprovalEvidenceBinding from(PolicyShadowEvidence evidence, String approvalPolicyVersion) {
        return new PolicyApprovalEvidenceBinding(
            evidence.shadowEvaluationId(), evidence.candidateRevision(),
            evidence.candidateArtifactDigest(),
            evidence.baselineArtifactId(), evidence.baselineArtifactVersion(), evidence.baselineArtifactDigest(),
            evidence.evaluationCaseId(), evidence.evaluationCaseVersion(), evidence.result(), approvalPolicyVersion
        );
    }
}
