package com.adp.gateway.recovery.domain;

public record RecoveryCommandResult(
    String recoveryId,
    String operationId,
    RecoveryOperationType operationType,
    RecoveryOperationOutcome outcome,
    RecoveryStatus recoveryStatus,
    String reasonCode,
    boolean replayed
) {
}
