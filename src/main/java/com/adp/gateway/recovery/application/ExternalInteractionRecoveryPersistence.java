package com.adp.gateway.recovery.application;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;

import com.adp.gateway.connector.domain.ConnectorResult;
import com.adp.gateway.recovery.domain.ExternalInteractionRecovery;
import com.adp.gateway.recovery.domain.RecoveryStatus;

public interface ExternalInteractionRecoveryPersistence {

    void scheduleUnknown(String executionId, ConnectorResult connectorResult, OffsetDateTime now);

    Optional<ExternalInteractionRecovery> claimNext(String workerId, OffsetDateTime now, Duration leaseDuration);

    RecoveryTransitionResult reschedule(
        String recoveryId,
        String workerId,
        OffsetDateTime nextAttemptAt,
        String errorCode
    );

    record RecoveryTransitionResult(boolean updated, RecoveryStatus resultingStatus) {

        public static RecoveryTransitionResult staleLease() {
            return new RecoveryTransitionResult(false, null);
        }
    }

    boolean reconcile(
        String recoveryId,
        String workerId,
        com.adp.gateway.recovery.domain.ExternalStatusQueryResult result,
        OffsetDateTime queriedAt
    );

    boolean markManualReview(String recoveryId, String workerId, String reasonCode);
}
