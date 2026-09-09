package com.adp.gateway.recovery.domain;

import java.time.OffsetDateTime;

public record RecoveryOperationEvent(
    String operationId,
    String actorPrincipalId,
    RecoveryOperationType operationType,
    RecoveryOperationOutcome outcome,
    String reasonCode,
    String evidenceDigest,
    OffsetDateTime createdAt,
    OffsetDateTime completedAt
) {
}
