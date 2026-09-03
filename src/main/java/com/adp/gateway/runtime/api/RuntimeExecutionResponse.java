package com.adp.gateway.runtime.api;

import com.adp.gateway.runtime.application.RuntimeExecutionResult;
import com.adp.gateway.runtime.application.IdempotentExecutionReplay;

import java.util.List;

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
    PrivacySafeOutputResponse privacySafeOutput,
    String outboundCandidateDigest,
    String outboundGuardStatus,
    String connectorStatus,
    String responseGuardStatus,
    ControlledDeliveryResponse output,
    String auditId,
    boolean replayed
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
            PrivacySafeOutputResponse.from(result.transformResult()),
            result.connectorResult().outboundCandidateDigest(),
            result.outboundGuardStatus(),
            result.connectorResult().status().name(),
            result.responseGuardStatus(),
            ControlledDeliveryResponse.from(result.controlledDelivery()),
            result.auditContext().auditId(),
            false
        );
    }

    public static RuntimeExecutionResponse from(IdempotentExecutionReplay replay) {
        PrivacySafeOutputResponse privacySafeOutput = replay.transformExecutionId() == null
            ? null
            : new PrivacySafeOutputResponse(
                replay.transformExecutionId(),
                replay.transformStatus(),
                replay.transformOutputDigest(),
                replay.transformedFieldCount() == null ? 0 : replay.transformedFieldCount(),
                List.of()
            );
        ControlledDeliveryResponse output = replay.controlledDeliveryStatus() == null
            ? null
            : new ControlledDeliveryResponse(
                replay.controlledDeliveryStatus(),
                null,
                replay.controlledDeliveryResponseDigest()
            );
        return new RuntimeExecutionResponse(
            replay.executionId(),
            replay.status(),
            replay.decisionId(),
            replay.policyAction(),
            replay.finalAction(),
            replay.authorizationResult(),
            replay.applicabilityResult(),
            replay.runtimeContextDigest(),
            replay.policyVersion(),
            replay.snapshotDigest(),
            replay.sourceArtifactId(),
            replay.sourceArtifactVersion(),
            replay.sourceArtifactDigestAlgorithm(),
            replay.sourceArtifactDigestValue(),
            privacySafeOutput,
            replay.outboundCandidateDigest(),
            replay.outboundGuardStatus(),
            replay.connectorStatus(),
            replay.responseGuardStatus(),
            output,
            replay.auditId(),
            true
        );
    }
}
