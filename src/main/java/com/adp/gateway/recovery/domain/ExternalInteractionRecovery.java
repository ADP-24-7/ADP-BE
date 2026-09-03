package com.adp.gateway.recovery.domain;

import java.time.OffsetDateTime;

import com.adp.gateway.connector.domain.ConnectorStatus;

public record ExternalInteractionRecovery(
    String recoveryId,
    String executionId,
    String connectorExecutionId,
    String connectorId,
    String providerCorrelationKey,
    ConnectorStatus observedStatus,
    ConnectorStatus lastObservedExternalStatus,
    RecoveryStatus recoveryStatus,
    RetryDisposition retryDisposition,
    int attemptCount,
    int maxAttempts,
    OffsetDateTime nextAttemptAt,
    String leaseOwner,
    OffsetDateTime leaseUntil,
    String lastErrorCode,
    OffsetDateTime lastStatusQueriedAt,
    String statusQueryEvidenceDigest
) {
}
