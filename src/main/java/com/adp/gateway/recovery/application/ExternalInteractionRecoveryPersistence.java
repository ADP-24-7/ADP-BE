package com.adp.gateway.recovery.application;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;

import com.adp.gateway.connector.domain.ConnectorResult;
import com.adp.gateway.recovery.domain.ExternalInteractionRecovery;

public interface ExternalInteractionRecoveryPersistence {

    void scheduleUnknown(String executionId, ConnectorResult connectorResult, OffsetDateTime now);

    Optional<ExternalInteractionRecovery> claimNext(String workerId, OffsetDateTime now, Duration leaseDuration);

    void reschedule(String recoveryId, String workerId, OffsetDateTime nextAttemptAt, String errorCode);

    void markReconciled(String recoveryId, String workerId);

    void markManualReview(String recoveryId, String workerId, String reasonCode);
}
