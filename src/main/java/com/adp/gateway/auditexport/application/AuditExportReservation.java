package com.adp.gateway.auditexport.application;

import java.time.OffsetDateTime;

import com.adp.gateway.auditexport.domain.AuditExportFormat;
import com.adp.gateway.egress.domain.ExecutionPackType;

public record AuditExportReservation(
    String exportId,
    String institutionId,
    String workloadId,
    ExecutionPackType executionPack,
    String executionId,
    String reportType,
    AuditExportFormat format,
    String scopeDigest,
    String scopeJson,
    String requesterId,
    String requestReason,
    String idempotencyKey,
    String requestId,
    String traceId,
    OffsetDateTime now
) {
}
