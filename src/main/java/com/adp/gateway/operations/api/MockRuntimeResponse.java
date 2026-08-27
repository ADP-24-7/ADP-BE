package com.adp.gateway.operations.api;

public record MockRuntimeResponse(
    String requestId,
    String traceId,
    String idempotencyKey,
    String policyArtifactId,
    String policyArtifactStatus,
    String decisionId,
    String outcome,
    String reasonCode,
    String connectorStatus,
    String auditId
) {
}
