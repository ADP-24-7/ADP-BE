package com.adp.gateway.recovery.application;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Set;

import com.adp.gateway.recovery.domain.RecoveryIncidentDetail;
import com.adp.gateway.recovery.domain.RecoveryIncidentPage;
import com.adp.gateway.recovery.domain.RecoveryOperationEvent;
import com.adp.gateway.recovery.domain.RecoveryOperationOutcome;
import com.adp.gateway.recovery.domain.RecoveryOperationType;
import com.adp.gateway.recovery.domain.RecoveryStatus;

public interface RecoveryOperationsPersistence {

    RecoveryIncidentPage search(
        String institutionId,
        Set<String> allowedWorkloads,
        RecoveryStatus status,
        int page,
        int size
    );

    RecoveryIncidentDetail load(String recoveryId, String institutionId, Set<String> allowedWorkloads);

    Optional<RecoveryOperationEvent> findOperation(
        String recoveryId,
        String operationId,
        String institutionId,
        Set<String> allowedWorkloads
    );

    boolean reserveOperation(
        String recoveryId,
        String operationId,
        String institutionId,
        Set<String> allowedWorkloads,
        String actorPrincipalId,
        RecoveryOperationType operationType,
        OffsetDateTime now
    );

    void completeOperation(
        String recoveryId,
        String operationId,
        RecoveryOperationOutcome outcome,
        String reasonCode,
        String evidenceDigest,
        OffsetDateTime now
    );
}
