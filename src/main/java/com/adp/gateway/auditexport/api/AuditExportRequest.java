package com.adp.gateway.auditexport.api;

import com.adp.gateway.auditexport.domain.AuditExportFormat;
import com.adp.gateway.auditexport.domain.AuditExportReportType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AuditExportRequest(
    @NotBlank @Size(max = 80) String executionId,
    @NotNull AuditExportReportType reportType,
    @NotNull AuditExportFormat format,
    @NotBlank @Size(max = 500) String reason,
    @NotBlank @Size(max = 120) String idempotencyKey
) {
}
