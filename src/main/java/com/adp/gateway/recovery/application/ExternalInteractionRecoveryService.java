package com.adp.gateway.recovery.application;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;

import com.adp.gateway.connector.domain.ConnectorStatus;
import com.adp.gateway.recovery.domain.ExternalStatusQueryResult;
import com.adp.gateway.observability.GatewayObservability;
import com.adp.gateway.observability.GatewayObservability.RecoveryOutcome;
import com.adp.gateway.recovery.domain.RecoveryStatus;
import com.adp.gateway.runtime.domain.RuntimeExecutionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ExternalInteractionRecoveryService {

    private static final Logger log = LoggerFactory.getLogger(ExternalInteractionRecoveryService.class);

    private static final Duration LEASE_DURATION = Duration.ofSeconds(30);
    private static final Duration RETRY_DELAY = Duration.ofMinutes(1);

    private final ExternalInteractionRecoveryPersistence persistence;
    private final ExternalStatusQueryResolver statusQueryResolver;
    private final Clock clock;
    private final GatewayObservability observability;

    public ExternalInteractionRecoveryService(
        ExternalInteractionRecoveryPersistence persistence,
        ExternalStatusQueryResolver statusQueryResolver,
        Clock clock,
        GatewayObservability observability
    ) {
        this.persistence = persistence;
        this.statusQueryResolver = statusQueryResolver;
        this.clock = clock;
        this.observability = observability;
    }

    public boolean processNext(String workerId) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        var claimResult = persistence.claimNext(workerId, now, LEASE_DURATION);
        recordExpiredClaims(claimResult.exhaustedCount());
        return claimResult.claimed()
            .map(recovery -> {
                try {
                    ExternalStatusQueryResult queryResult = statusQueryResolver
                        .resolve(recovery.connectorId())
                        .query(recovery);
                    ConnectorStatus status = queryResult.status();
                    if (status == ConnectorStatus.ACKNOWLEDGED || status == ConnectorStatus.COMPLETED) {
                        requireLease(persistence.reconcile(recovery.recoveryId(), workerId, queryResult, now));
                        observability.runtimeExecution(RuntimeExecutionStatus.EXTERNALLY_RECONCILED);
                        record(RecoveryOutcome.RECONCILED);
                    } else if (status == ConnectorStatus.SENT_UNKNOWN) {
                        recordReschedule(persistence.reschedule(
                            recovery.recoveryId(), workerId, now.plus(RETRY_DELAY), "STILL_SENT_UNKNOWN"
                        ));
                    } else {
                        requireLease(persistence.markManualReview(
                            recovery.recoveryId(), workerId, "EXTERNAL_STATUS_" + status.name()
                        ));
                        recordManualReview();
                    }
                } catch (ExternalStatusQueryUnavailableException exception) {
                    recordReschedule(persistence.reschedule(
                        recovery.recoveryId(), workerId, now.plus(RETRY_DELAY), "STATUS_QUERY_UNAVAILABLE"
                    ));
                } catch (ExternalStatusQueryPermanentException exception) {
                    requireLease(persistence.markManualReview(
                        recovery.recoveryId(), workerId, "STATUS_QUERY_PERMANENT_FAILURE"
                    ));
                    recordManualReview();
                } catch (AmbiguousExternalStatusQueryAdapterException exception) {
                    requireLease(persistence.markManualReview(
                        recovery.recoveryId(), workerId, "STATUS_QUERY_ADAPTER_AMBIGUOUS"
                    ));
                    recordManualReview();
                } catch (StaleRecoveryLeaseException exception) {
                    record(RecoveryOutcome.STALE_LEASE);
                    throw exception;
                } catch (RuntimeException exception) {
                    recordReschedule(persistence.reschedule(
                        recovery.recoveryId(), workerId, now.plus(RETRY_DELAY), "INTERNAL_QUERY_ERROR"
                    ));
                }
                return true;
            })
            .orElse(false);
    }

    private void requireLease(boolean updated) {
        if (!updated) {
            throw new StaleRecoveryLeaseException();
        }
    }

    private void recordReschedule(ExternalInteractionRecoveryPersistence.RecoveryTransitionResult transition) {
        if (!transition.updated()) {
            throw new StaleRecoveryLeaseException();
        }
        if (transition.resultingStatus() == RecoveryStatus.EXHAUSTED) {
            observability.runtimeExecution(RuntimeExecutionStatus.REVIEW_REQUIRED);
            record(RecoveryOutcome.EXHAUSTED);
            return;
        }
        record(RecoveryOutcome.RESCHEDULED);
    }

    private void recordManualReview() {
        observability.runtimeExecution(RuntimeExecutionStatus.REVIEW_REQUIRED);
        record(RecoveryOutcome.MANUAL_REVIEW);
    }

    private void recordExpiredClaims(int exhaustedCount) {
        if (exhaustedCount == 0) {
            return;
        }
        observability.recovery(RecoveryOutcome.EXHAUSTED, exhaustedCount);
        observability.runtimeExecution(RuntimeExecutionStatus.REVIEW_REQUIRED, exhaustedCount);
        log.atWarn()
            .addKeyValue("event", "recovery_expired_claims")
            .addKeyValue("outcome", RecoveryOutcome.EXHAUSTED.name())
            .addKeyValue("count", exhaustedCount)
            .log("Expired recovery claims exhausted retry attempts");
    }

    private void record(RecoveryOutcome outcome) {
        observability.recovery(outcome);
        log.atInfo()
            .addKeyValue("event", "recovery_processing")
            .addKeyValue("outcome", outcome.name())
            .log("External interaction recovery processed");
    }
}
