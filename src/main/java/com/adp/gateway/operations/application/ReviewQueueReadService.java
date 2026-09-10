package com.adp.gateway.operations.application;

import com.adp.gateway.auth.domain.AdpRole;
import com.adp.gateway.auth.domain.AuthPrincipal;
import com.adp.gateway.egress.domain.ExecutionPackType;
import com.adp.gateway.operations.domain.ReviewQueueDetail;
import com.adp.gateway.operations.domain.ReviewQueuePage;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
public class ReviewQueueReadService {
    private final ReviewQueueReadPort port;

    public ReviewQueueReadService(ReviewQueueReadPort port) {
        this.port = port;
    }

    public ReviewQueuePage search(
        AuthPrincipal principal,
        ExecutionPackType executionPack,
        String workloadId,
        int page,
        int size
    ) {
        requireReader(principal);
        if (workloadId != null && !principal.canAccessWorkload(workloadId)) {
            throw new AccessDeniedException("Review queue workload is not visible to this principal");
        }
        return port.search(
            principal.institutionId(), principal.workloadIds(), executionPack, workloadId, page, size
        );
    }

    public ReviewQueueDetail load(AuthPrincipal principal, String executionId) {
        requireReader(principal);
        return port.load(executionId, principal.institutionId(), principal.workloadIds());
    }

    private void requireReader(AuthPrincipal principal) {
        if (principal == null || principal.institutionId() == null || principal.institutionId().isBlank()) {
            throw new AccessDeniedException("Review queue institution scope is required");
        }
        if (!principal.hasRole(AdpRole.OPERATOR) && !principal.hasRole(AdpRole.PRIVILEGED_OPERATOR)
            && !principal.hasRole(AdpRole.AUDITOR)) {
            throw new AccessDeniedException("Review queue access is forbidden");
        }
    }
}
