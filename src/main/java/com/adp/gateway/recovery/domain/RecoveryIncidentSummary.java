package com.adp.gateway.recovery.domain;

import java.time.OffsetDateTime;

import com.adp.gateway.connector.domain.ConnectorStatus;
import com.adp.gateway.egress.domain.ExecutionPackType;

public record RecoveryIncidentSummary(
    String recoveryId,
    String executionId,
    String institutionId,
    ExecutionPackType executionPack,
    String workloadId,
    String purposeCode,
    String connectorId,
    ConnectorStatus observedStatus,
    ConnectorStatus lastObservedExternalStatus,
    RecoveryStatus recoveryStatus,
    RetryDisposition retryDisposition,
    int attemptCount,
    int maxAttempts,
    OffsetDateTime nextAttemptAt,
    String lastErrorCode,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
}
