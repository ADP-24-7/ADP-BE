package com.adp.gateway.operations.application;

import com.adp.gateway.auth.domain.AdpRole;
import com.adp.gateway.auth.domain.AuthPrincipal;
import com.adp.gateway.auth.domain.PrincipalType;
import com.adp.gateway.operations.domain.AdminIdentityDetail;
import com.adp.gateway.operations.domain.AdminIdentityPage;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
public class AdminIdentityReadService {
    private final AdminIdentityReadPort port;

    public AdminIdentityReadService(AdminIdentityReadPort port) {
        this.port = port;
    }

    public AdminIdentityPage search(
        AuthPrincipal principal,
        PrincipalType principalType,
        AdpRole role,
        String workloadId,
        Boolean enabled,
        String query,
        int page,
        int size
    ) {
        requireReader(principal);
        if (workloadId != null && !principal.canAccessWorkload(workloadId)) {
            throw new AccessDeniedException("Identity workload is not visible to this principal");
        }
        return port.search(
            principal.institutionId(), principal.workloadIds(), principalType, role,
            workloadId, enabled, query, page, size
        );
    }

    public AdminIdentityDetail load(AuthPrincipal principal, String principalId) {
        requireReader(principal);
        return port.load(principalId, principal.institutionId(), principal.workloadIds());
    }

    private void requireReader(AuthPrincipal principal) {
        if (principal == null || principal.institutionId() == null || principal.institutionId().isBlank()) {
            throw new AccessDeniedException("Identity institution scope is required");
        }
        if (!principal.hasRole(AdpRole.OPERATOR) && !principal.hasRole(AdpRole.PRIVILEGED_OPERATOR)
            && !principal.hasRole(AdpRole.AUDITOR)) {
            throw new AccessDeniedException("Identity access is forbidden");
        }
    }
}
