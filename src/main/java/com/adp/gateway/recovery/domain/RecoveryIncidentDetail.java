package com.adp.gateway.recovery.domain;

import java.time.OffsetDateTime;
import java.util.List;

import com.adp.gateway.connector.domain.ConnectorStatus;
import com.adp.gateway.egress.domain.ExecutionPackType;

public record RecoveryIncidentDetail(
    String recoveryId,
    String executionId,
    String institutionId,
    ExecutionPackType executionPack,
    String workloadId,
    String purposeCode,
    String connectorExecutionId,
    String connectorId,
    ConnectorStatus observedStatus,
    ConnectorStatus lastObservedExternalStatus,
    RecoveryStatus recoveryStatus,
    RetryDisposition retryDisposition,
    int attemptCount,
    int maxAttempts,
    OffsetDateTime nextAttemptAt,
    OffsetDateTime leaseUntil,
    String lastErrorCode,
    OffsetDateTime lastStatusQueriedAt,
    String statusQueryEvidenceDigest,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    List<RecoveryOperationEvent> operations
) {
    public RecoveryIncidentDetail {
        operations = List.copyOf(operations);
    }
}
