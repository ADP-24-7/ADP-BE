package com.adp.gateway.auditexport.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AuditExportApprovalRequest(
    @NotNull Action action,
    @NotBlank @Size(max = 500) String reason
) {
    public enum Action {
        APPROVE,
        REJECT,
        REVOKE
    }
}
