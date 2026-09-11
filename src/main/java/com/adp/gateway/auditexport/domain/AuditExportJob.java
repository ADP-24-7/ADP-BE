package com.adp.gateway.auditexport.domain;

import java.time.OffsetDateTime;

import com.adp.gateway.egress.domain.ExecutionPackType;
import com.fasterxml.jackson.annotation.JsonIgnore;

public record AuditExportJob(
    String exportId,
    String institutionId,
    String workloadId,
    ExecutionPackType executionPack,
    String executionId,
    String reportType,
    AuditExportFormat format,
    AuditExportStatus status,
    String scopeDigest,
    String requesterId,
    String approverId,
    String requestReason,
    String approvalReason,
    String revokedBy,
    OffsetDateTime revokedAt,
    String revocationReason,
    @JsonIgnore String idempotencyKey,
    @JsonIgnore String scopeJson,
    Integer rowCount,
    String contentDigest,
    Long contentSize,
    String contentType,
    String fileName,
    String failureCode,
    OffsetDateTime createdAt,
    OffsetDateTime approvedAt,
    OffsetDateTime generatedAt,
    OffsetDateTime expiresAt,
    OffsetDateTime downloadedAt,
    OffsetDateTime updatedAt,
    long version
) {
}
