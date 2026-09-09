package com.adp.gateway.observability.domain;

import java.time.OffsetDateTime;

public record OperationsSummary(
    String schemaVersion,
    int windowMinutes,
    OffsetDateTime generatedAt,
    RuntimeHealth runtime,
    RecoveryHealth recovery,
    PolicyHealth policy,
    SecurityHealth security
) {
    public record RuntimeHealth(
        long total,
        long completed,
        long failed,
        long blocked,
        long reviewRequired
    ) {
    }

    public record RecoveryHealth(
        long backlog,
        Long oldestBacklogAgeSeconds,
        long manualReview,
        long exhausted,
        long completedOperations,
        Long averageOperationLatencyMillis,
        long staleOperations,
        Long oldestStaleOperationAgeSeconds
    ) {
    }

    public record PolicyHealth(
        long currentSelections,
        long driftedSelections,
        long activations,
        long rollbacks
    ) {
    }

    public record SecurityHealth(
        long deniedAttempts,
        long institutionScopeMismatch,
        long authorizationPolicyDenied
    ) {
    }
}
