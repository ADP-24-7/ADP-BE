package com.adp.gateway.audit.application;

import java.time.OffsetDateTime;

import com.adp.gateway.audit.domain.AuditExecutionPage;
import com.adp.gateway.audit.domain.ExecutionEvidencePack;
import com.adp.gateway.auth.domain.AuthPrincipal;
import com.adp.gateway.runtime.application.RuntimeExecutionNotFoundException;
import com.adp.gateway.runtime.domain.RuntimeExecutionStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
public class AuditReadService {
    private final AuditReadPort auditReadPort;

    public AuditReadService(AuditReadPort auditReadPort) {
        this.auditReadPort = auditReadPort;
    }

    public AuditExecutionPage search(
        AuthPrincipal principal,
        String workloadId,
        String status,
        OffsetDateTime from,
        OffsetDateTime to,
        int page,
        int size
    ) {
        requireInstitution(principal);
        if (workloadId != null && !principal.canAccessWorkload(workloadId)) {
            throw new AccessDeniedException("Audit workload is not visible to this principal");
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidAuditSearchException("from must not be after to");
        }
        return auditReadPort.search(
            principal.institutionId(), principal.workloadIds(), workloadId,
            validatedStatus(status), from, to, page, size
        );
    }

    public ExecutionEvidencePack evidence(AuthPrincipal principal, String executionId) {
        requireInstitution(principal);
        ExecutionEvidencePack evidence = auditReadPort.loadEvidence(
            executionId, principal.institutionId(), principal.workloadIds()
        );
        if (!principal.institutionId().equals(evidence.institutionId())
            || !principal.canAccessWorkload(evidence.workloadId())) {
            throw new RuntimeExecutionNotFoundException(executionId);
        }
        return evidence;
    }

    private void requireInstitution(AuthPrincipal principal) {
        if (principal.institutionId() == null || principal.institutionId().isBlank()) {
            throw new AccessDeniedException("Audit institution scope is required");
        }
    }

    private String validatedStatus(String status) {
        if (status == null) {
            return null;
        }
        try {
            return RuntimeExecutionStatus.valueOf(status).name();
        } catch (IllegalArgumentException exception) {
            throw new InvalidAuditSearchException("Unsupported runtime status");
        }
    }
}
