package com.adp.gateway.operations.api;

public record MockRuntimeResponse(
    String requestId,
    String traceId,
    String idempotencyKey,
    String policyArtifactId,
    String policyArtifactStatus,
    String policyVersion,
    String policyDigest,
    String decisionId,
    String policyAction,
    String finalAction,
    String authorizationResult,
    String applicabilityResult,
    String runtimeContextDigest,
    String matchedRuleIds,
    String evidenceRefs,
    String requiredControls,
    String outcome,
    String reasonCode,
    String connectorStatus,
    String auditId
) {
}
