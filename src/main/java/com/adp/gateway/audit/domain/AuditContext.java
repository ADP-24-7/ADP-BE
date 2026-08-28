package com.adp.gateway.audit.domain;

import java.time.OffsetDateTime;

public record AuditContext(
    String auditId,
    String requestId,
    String traceId,
    String idempotencyKey,
    String workloadId,
    String decisionId,
    String policyArtifactId,
    String policyAction,
    String finalAction,
    String authorizationResult,
    String applicabilityResult,
    String matchedRuleIds,
    String policyVersion,
    String policyDigest,
    String reasonCode,
    String connectorStatus,
    OffsetDateTime createdAt
) {
}
