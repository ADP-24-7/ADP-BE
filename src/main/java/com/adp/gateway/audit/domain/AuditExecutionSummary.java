package com.adp.gateway.audit.domain;

import java.time.OffsetDateTime;

public record AuditExecutionSummary(
    String executionId,
    String requestId,
    String traceId,
    String institutionId,
    String workloadId,
    String purposeCode,
    String status,
    String finalAction,
    String policyVersion,
    String snapshotDigest,
    String destinationProfileId,
    String destinationProfileVersion,
    String connectorStatus,
    String recoveryStatus,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
}
