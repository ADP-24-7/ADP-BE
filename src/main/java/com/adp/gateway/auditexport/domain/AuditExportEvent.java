package com.adp.gateway.auditexport.domain;

import java.time.OffsetDateTime;

public record AuditExportEvent(
    String eventId,
    String actorId,
    String requestId,
    String traceId,
    String action,
    AuditExportStatus fromStatus,
    AuditExportStatus toStatus,
    String reasonCode,
    String reasonText,
    OffsetDateTime occurredAt
) {
}
