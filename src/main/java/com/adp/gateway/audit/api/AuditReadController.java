package com.adp.gateway.audit.api;

import java.time.OffsetDateTime;

import com.adp.gateway.audit.application.AuditReadService;
import com.adp.gateway.audit.domain.AuditExecutionPage;
import com.adp.gateway.audit.domain.ExecutionEvidencePack;
import com.adp.gateway.auth.domain.AuthPrincipal;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/admin/audit/executions")
public class AuditReadController {
    private final AuditReadService auditReadService;

    public AuditReadController(AuditReadService auditReadService) {
        this.auditReadService = auditReadService;
    }

    @GetMapping
    AuditExecutionPage search(
        @RequestParam(required = false) @Size(max = 120) String workloadId,
        @RequestParam(required = false) @Size(max = 40) String status,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
        @RequestParam(defaultValue = "0") @Min(0) @Max(1000000) int page,
        @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size,
        Authentication authentication
    ) {
        return auditReadService.search(principal(authentication), blankToNull(workloadId),
            blankToNull(status), from, to, page, size);
    }

    @GetMapping("/{executionId}/evidence")
    ExecutionEvidencePack evidence(
        @PathVariable @Size(max = 80) String executionId,
        Authentication authentication
    ) {
        return auditReadService.evidence(principal(authentication), executionId);
    }

    private AuthPrincipal principal(Authentication authentication) {
        return (AuthPrincipal) authentication.getPrincipal();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
