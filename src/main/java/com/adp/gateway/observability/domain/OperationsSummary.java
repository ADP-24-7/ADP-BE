package com.adp.gateway.observability.domain;

import java.time.OffsetDateTime;
import java.util.List;

import com.adp.gateway.egress.domain.ExecutionPackType;

public record OperationsSummary(
    String schemaVersion,
    int windowMinutes,
    OffsetDateTime generatedAt,
    Scope scope,
    RuntimeHealth runtime,
    RecoveryHealth recovery,
    PolicyHealth policy,
    SecurityHealth security
) {
    public record Scope(
        ExecutionPackType requestedExecutionPack,
        String defaultSemantics,
        List<String> packScopedSections,
        List<String> allAuthorizedWorkloadSections
    ) {
        public Scope {
            packScopedSections = List.copyOf(packScopedSections);
            allAuthorizedWorkloadSections = List.copyOf(allAuthorizedWorkloadSections);
        }
    }

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
