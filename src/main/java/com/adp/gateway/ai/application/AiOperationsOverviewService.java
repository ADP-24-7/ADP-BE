package com.adp.gateway.ai.application;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Set;

import com.adp.gateway.ai.domain.AiOperationsOverview;
import com.adp.gateway.auth.domain.AdpRole;
import com.adp.gateway.auth.domain.AuthPrincipal;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
public class AiOperationsOverviewService {
    private static final Duration DEFAULT_WINDOW = Duration.ofDays(7);
    private static final Duration MAX_WINDOW = Duration.ofDays(31);
    private static final Set<String> EXECUTION_STATUSES = Set.of(
        "COMPLETED", "BLOCKED", "FAILED", "EGRESSING", "EXTERNALLY_RECONCILED", "REVIEW_REQUIRED"
    );

    private final AiOperationsOverviewPort port;
    private final Clock clock;

    public AiOperationsOverviewService(AiOperationsOverviewPort port, Clock clock) {
        this.port = port;
        this.clock = clock;
    }

    public AiOperationsOverview load(
        AuthPrincipal principal,
        OffsetDateTime requestedFrom,
        OffsetDateTime requestedTo,
        String executionQuery,
        String executionStatus,
        int executionPage,
        int executionSize
    ) {
        requireReader(principal);
        OffsetDateTime now = OffsetDateTime.now(clock);
        OffsetDateTime to = requestedTo == null ? now : requestedTo;
        OffsetDateTime from = requestedFrom == null ? to.minus(DEFAULT_WINDOW) : requestedFrom;
        if (!from.isBefore(to) || Duration.between(from, to).compareTo(MAX_WINDOW) > 0 || to.isAfter(now.plusMinutes(1))) {
            throw new InvalidAiOverviewRangeException();
        }
        String query = executionQuery == null ? "" : executionQuery.trim();
        String status = executionStatus == null ? "" : executionStatus.trim().toUpperCase();
        if (query.length() > 120 || (!status.isEmpty() && !EXECUTION_STATUSES.contains(status))
            || executionPage < 0 || executionSize < 1 || executionSize > 50) {
            throw new InvalidAiOverviewRangeException();
        }
        return port.load(
            principal.institutionId(), principal.workloadIds(), from, to, now,
            query, status, executionPage, executionSize
        );
    }

    private void requireReader(AuthPrincipal principal) {
        if (principal == null || principal.institutionId() == null || principal.institutionId().isBlank()
            || (!principal.hasRole(AdpRole.OPERATOR) && !principal.hasRole(AdpRole.PRIVILEGED_OPERATOR)
                && !principal.hasRole(AdpRole.AUDITOR))) {
            throw new AccessDeniedException("AI operations overview access is forbidden");
        }
    }
}
