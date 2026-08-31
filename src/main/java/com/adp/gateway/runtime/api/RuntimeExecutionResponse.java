package com.adp.gateway.runtime.api;

import com.adp.gateway.runtime.application.RuntimeExecutionResult;

public record RuntimeExecutionResponse(
    String executionId,
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
    String connectorStatus,
    String auditId
) {

    public static RuntimeExecutionResponse from(RuntimeExecutionResult result) {
        var decision = result.decision();
        var source = decision.sourcePolicyEvaluationArtifactRef();
        return new RuntimeExecutionResponse(
            result.executionId(),
            result.status().name(),
            decision.decisionId(),
            decision.policyAction().name(),
            decision.finalAction().name(),
            decision.authorizationResult().name(),
            decision.applicabilityResult().name(),
            decision.runtimeContextDigest(),
            decision.policyVersion(),
            decision.snapshotDigest(),
            source.artifactId(),
            source.artifactVersion(),
            source.artifactDigest().algorithm(),
            source.artifactDigest().value(),
            result.connectorResult().status(),
            result.auditContext().auditId()
        );
    }
}
