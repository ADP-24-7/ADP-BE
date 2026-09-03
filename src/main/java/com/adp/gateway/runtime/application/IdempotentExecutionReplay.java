package com.adp.gateway.runtime.application;

public record IdempotentExecutionReplay(
    String executionId,
    String requestHash,
    String status,
    String decisionId,
    String policyAction,
    String finalAction,
    String authorizationResult,
    String applicabilityResult,
    String runtimeContextDigest,
    String policyVersion,
    String snapshotDigest,
    String sourceArtifactId,
    String sourceArtifactVersion,
    String sourceArtifactDigestAlgorithm,
    String sourceArtifactDigestValue,
    String transformExecutionId,
    String transformStatus,
    String transformOutputDigest,
    Integer transformedFieldCount,
    String outboundCandidateDigest,
    String outboundGuardStatus,
    String connectorStatus,
    String responseGuardStatus,
    String controlledDeliveryStatus,
    String controlledDeliveryResponseDigest,
    String auditId
) {

    public boolean inProgress() {
        return switch (status) {
            case "RECEIVED", "AUTHORIZED", "RETRIEVED", "DECIDED", "TRANSFORMED", "EGRESSING" -> true;
            default -> false;
        };
    }
}
