package com.adp.gateway.auditexport.domain;

import com.adp.gateway.egress.domain.ExecutionPackType;

public record AuditExportScope(
    String institutionId,
    String workloadId,
    ExecutionPackType executionPack,
    String executionId
) {
}
