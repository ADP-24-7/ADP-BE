package com.adp.gateway.operations.application;

import java.time.OffsetDateTime;

import com.adp.gateway.auth.domain.AdpRole;
import com.adp.gateway.auth.domain.AuthPrincipal;
import com.adp.gateway.egress.domain.ExecutionPackType;
import com.adp.gateway.operations.domain.SecurityFindingDetail;
import com.adp.gateway.operations.domain.SecurityFindingPage;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
public class SecurityFindingReadService {
    private final SecurityFindingReadPort port;

    public SecurityFindingReadService(SecurityFindingReadPort port) {
        this.port = port;
    }

    public SecurityFindingPage search(
        AuthPrincipal principal,
        ExecutionPackType executionPack,
        String workloadId,
        String findingType,
        OffsetDateTime from,
        OffsetDateTime to,
        int page,
        int size
    ) {
        requireReader(principal);
        if (workloadId != null && !principal.canAccessWorkload(workloadId)) {
            throw new AccessDeniedException("Security finding workload is not visible to this principal");
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidSecurityFindingSearchException("Security finding from must not be after to");
        }
        return port.search(
            principal.institutionId(), principal.workloadIds(), executionPack,
            workloadId, findingType, from, to, page, size
        );
    }

    public SecurityFindingDetail load(AuthPrincipal principal, long findingId) {
        requireReader(principal);
        return port.load(findingId, principal.institutionId(), principal.workloadIds());
    }

    private void requireReader(AuthPrincipal principal) {
        if (principal == null || principal.institutionId() == null || principal.institutionId().isBlank()) {
            throw new AccessDeniedException("Security finding institution scope is required");
        }
        if (!principal.hasRole(AdpRole.OPERATOR) && !principal.hasRole(AdpRole.PRIVILEGED_OPERATOR)
            && !principal.hasRole(AdpRole.AUDITOR)) {
            throw new AccessDeniedException("Security finding access is forbidden");
        }
    }
}
