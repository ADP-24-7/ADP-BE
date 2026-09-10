package com.adp.gateway.observability.application;

import java.time.Clock;
import java.time.OffsetDateTime;

import com.adp.gateway.auth.domain.AdpRole;
import com.adp.gateway.auth.domain.AuthPrincipal;
import com.adp.gateway.egress.domain.ExecutionPackType;
import com.adp.gateway.observability.domain.OperationsSummary;
import com.adp.gateway.observability.domain.PolicyOperationEvent.PolicyEventCategory;
import com.adp.gateway.observability.domain.PolicyOperationEventPage;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
public class OperationsMonitoringService {

    private final OperationsMonitoringPort port;
    private final Clock clock;

    public OperationsMonitoringService(OperationsMonitoringPort port, Clock clock) {
        this.port = port;
        this.clock = clock;
    }

    public OperationsSummary summary(
        AuthPrincipal principal,
        ExecutionPackType executionPack,
        int windowMinutes
    ) {
        requireReader(principal);
        if (windowMinutes < 5 || windowMinutes > 1440) {
            throw new IllegalArgumentException("Monitoring window must be between 5 and 1440 minutes");
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        return port.loadSummary(
            principal.institutionId(), principal.workloadIds(), executionPack,
            now.minusMinutes(windowMinutes), now, windowMinutes
        );
    }

    public PolicyOperationEventPage policyEvents(
        AuthPrincipal principal,
        ExecutionPackType executionPack,
        String workloadId,
        PolicyEventCategory category,
        OffsetDateTime from,
        OffsetDateTime to,
        int page,
        int size
    ) {
        requireReader(principal);
        if (workloadId != null && !principal.canAccessWorkload(workloadId)) {
            throw new AccessDeniedException("Policy event workload scope is forbidden");
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("Policy event time range is invalid");
        }
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("Policy event pagination is invalid");
        }
        return port.loadPolicyEvents(
            principal.institutionId(), principal.workloadIds(), executionPack,
            workloadId, category, from, to, page, size
        );
    }

    private void requireReader(AuthPrincipal principal) {
        if (principal == null || principal.institutionId() == null || principal.institutionId().isBlank()
            || (!principal.hasRole(AdpRole.OPERATOR) && !principal.hasRole(AdpRole.PRIVILEGED_OPERATOR)
                && !principal.hasRole(AdpRole.AUDITOR))) {
            throw new AccessDeniedException("Operations monitoring access is forbidden");
        }
    }
}
