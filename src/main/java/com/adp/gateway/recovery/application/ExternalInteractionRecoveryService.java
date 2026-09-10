package com.adp.gateway.recovery.application;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;

import com.adp.gateway.connector.domain.ConnectorStatus;
import com.adp.gateway.recovery.domain.ExternalStatusQueryResult;
import com.adp.gateway.recovery.domain.ExternalRetryResult;
import com.adp.gateway.observability.GatewayObservability;
import com.adp.gateway.observability.GatewayObservability.RecoveryOutcome;
import com.adp.gateway.recovery.domain.RecoveryStatus;
import com.adp.gateway.recovery.domain.RetryDisposition;
import com.adp.gateway.runtime.domain.RuntimeExecutionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ExternalInteractionRecoveryService {

    private static final Logger log = LoggerFactory.getLogger(ExternalInteractionRecoveryService.class);

    private final ExternalInteractionRecoveryPersistence persistence;
    private final ExternalStatusQueryResolver statusQueryResolver;
    private final ExternalRetryResolver retryResolver;
    private final RecoveryReconciliationCoordinator reconciliationCoordinator;
    private final RecoveryBackoffPolicy backoffPolicy;
    private final Duration leaseDuration;
    private final Clock clock;
    private final GatewayObservability observability;

    public ExternalInteractionRecoveryService(
        ExternalInteractionRecoveryPersistence persistence,
        ExternalStatusQueryResolver statusQueryResolver,
        ExternalRetryResolver retryResolver,
        RecoveryReconciliationCoordinator reconciliationCoordinator,
        RecoveryBackoffPolicy backoffPolicy,
        @Value("${adp.recovery.lease-duration:30s}") Duration leaseDuration,
        Clock clock,
        GatewayObservability observability
    ) {
        this.persistence = persistence;
        this.statusQueryResolver = statusQueryResolver;
        this.retryResolver = retryResolver;
        this.reconciliationCoordinator = reconciliationCoordinator;
        this.backoffPolicy = backoffPolicy;
        if (leaseDuration.isNegative() || leaseDuration.isZero()) {
            throw new IllegalArgumentException("Recovery lease duration must be positive");
        }
        this.leaseDuration = leaseDuration;
        this.clock = clock;
        this.observability = observability;
    }

    public boolean processNext(String workerId) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        var claimResult = persistence.claimNext(workerId, now, leaseDuration);
        recordExpiredClaims(claimResult.exhaustedCount());
        return claimResult.claimed()
            .map(recovery -> {
                processClaimed(recovery, workerId, true);
                return true;
            })
            .orElse(false);
    }

    public RecoveryProcessingResult processClaimed(
        com.adp.gateway.recovery.domain.ExternalInteractionRecovery recovery,
        String workerId,
        boolean allowRetry
    ) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        try {
            ExternalStatusQueryResult queryResult = statusQueryResolver
                .resolve(recovery.connectorId())
                .query(recovery);
            ConnectorStatus status = queryResult.status();
            if (status == ConnectorStatus.ACKNOWLEDGED || status == ConnectorStatus.COMPLETED) {
                return reconcile(recovery, workerId, queryResult, now);
            }
            if (status == ConnectorStatus.SENT_UNKNOWN) {
                return observedReschedule(
                    recovery, workerId, queryResult, RetryDisposition.RECONCILE_FIRST,
                    now, "STILL_SENT_UNKNOWN"
                );
            }
            if (status == ConnectorStatus.NOT_SENT) {
                if (!allowRetry) {
                    return observedReschedule(
                        recovery, workerId, queryResult, RetryDisposition.RETRY_ALLOWED,
                        now, "NOT_SENT_CONFIRMED"
                    );
                }
                try {
                    return retryConfirmedNotSent(recovery, workerId, now);
                } catch (ExternalRetryUnavailableException exception) {
                    return observedReschedule(
                        recovery, workerId, queryResult, RetryDisposition.RETRY_ALLOWED,
                        now, "SAFE_RETRY_ADAPTER_UNAVAILABLE"
                    );
                } catch (AmbiguousExternalStatusQueryAdapterException exception) {
                    return observedReschedule(
                        recovery, workerId, queryResult, RetryDisposition.MANUAL_REVIEW,
                        now, "SAFE_RETRY_ADAPTER_AMBIGUOUS"
                    );
                }
            }
            return observedReschedule(
                recovery, workerId, queryResult, RetryDisposition.MANUAL_REVIEW,
                now, "EXTERNAL_STATUS_" + status.name()
            );
        } catch (ExternalStatusQueryUnavailableException exception) {
            return reschedule(recovery, workerId, now, "STATUS_QUERY_UNAVAILABLE");
        } catch (ExternalStatusQueryPermanentException exception) {
            return manualReview(recovery, workerId, "STATUS_QUERY_PERMANENT_FAILURE", null);
        } catch (AmbiguousExternalStatusQueryAdapterException exception) {
            return manualReview(recovery, workerId, "RECOVERY_ADAPTER_AMBIGUOUS", null);
        } catch (StaleRecoveryLeaseException exception) {
            record(RecoveryOutcome.STALE_LEASE);
            throw exception;
        } catch (RuntimeException exception) {
            return reschedule(recovery, workerId, now, "INTERNAL_RECOVERY_ERROR");
        }
    }

    public RecoveryProcessingResult markClaimedManualReview(
        com.adp.gateway.recovery.domain.ExternalInteractionRecovery recovery,
        String workerId
    ) {
        return manualReview(recovery, workerId, "OPERATOR_MARKED_MANUAL_REVIEW", null);
    }

    private RecoveryProcessingResult retryConfirmedNotSent(
        com.adp.gateway.recovery.domain.ExternalInteractionRecovery recovery,
        String workerId,
        OffsetDateTime now
    ) {
        ExternalRetryResult retryResult = retryResolver.resolve(recovery.connectorId()).retry(recovery);
        ExternalStatusQueryResult result = new ExternalStatusQueryResult(
            retryResult.status(), retryResult.evidenceDigest()
        );
        if (retryResult.status() == ConnectorStatus.ACKNOWLEDGED
            || retryResult.status() == ConnectorStatus.COMPLETED) {
            return reconcile(recovery, workerId, result, now);
        }
        if (retryResult.status() == ConnectorStatus.SENT_UNKNOWN) {
            return observedReschedule(
                recovery, workerId, result, RetryDisposition.RECONCILE_FIRST,
                now, "RETRY_RESULT_SENT_UNKNOWN"
            );
        }
        if (retryResult.status() == ConnectorStatus.NOT_SENT) {
            return observedReschedule(
                recovery, workerId, result, RetryDisposition.RETRY_ALLOWED,
                now, "RETRY_NOT_SENT"
            );
        }
        return observedReschedule(
            recovery, workerId, result, RetryDisposition.MANUAL_REVIEW,
            now, "SAFE_RETRY_FAILED"
        );
    }

    private RecoveryProcessingResult reconcile(
        com.adp.gateway.recovery.domain.ExternalInteractionRecovery recovery,
        String workerId,
        ExternalStatusQueryResult result,
        OffsetDateTime now
    ) {
        reconciliationCoordinator.commit(recovery, workerId, result, now);
        observability.runtimeExecution(RuntimeExecutionStatus.EXTERNALLY_RECONCILED);
        record(RecoveryOutcome.RECONCILED);
        return new RecoveryProcessingResult(RecoveryStatus.RECONCILED, "EXTERNAL_STATUS_CONFIRMED",
            result.evidenceDigest());
    }

    private RecoveryProcessingResult observedReschedule(
        com.adp.gateway.recovery.domain.ExternalInteractionRecovery recovery,
        String workerId,
        ExternalStatusQueryResult result,
        RetryDisposition disposition,
        OffsetDateTime now,
        String reasonCode
    ) {
        RecoveryStatus status = recordReschedule(persistence.recordObservedAndReschedule(
            recovery.recoveryId(), workerId, result, disposition,
            now.plus(backoffPolicy.delayFor(recovery.attemptCount())), reasonCode
        ));
        return new RecoveryProcessingResult(status, reasonCode, result.evidenceDigest());
    }

    private RecoveryProcessingResult reschedule(
        com.adp.gateway.recovery.domain.ExternalInteractionRecovery recovery,
        String workerId,
        OffsetDateTime now,
        String reasonCode
    ) {
        RecoveryStatus status = recordReschedule(persistence.reschedule(
            recovery.recoveryId(), workerId,
            now.plus(backoffPolicy.delayFor(recovery.attemptCount())), reasonCode
        ));
        return new RecoveryProcessingResult(status, reasonCode, null);
    }

    private RecoveryProcessingResult manualReview(
        com.adp.gateway.recovery.domain.ExternalInteractionRecovery recovery,
        String workerId,
        String reasonCode,
        String evidenceDigest
    ) {
        requireLease(persistence.markManualReview(recovery.recoveryId(), workerId, reasonCode));
        recordManualReview();
        return new RecoveryProcessingResult(RecoveryStatus.MANUAL_REVIEW, reasonCode, evidenceDigest);
    }

    private void requireLease(boolean updated) {
        if (!updated) {
            throw new StaleRecoveryLeaseException();
        }
    }

    private RecoveryStatus recordReschedule(
        ExternalInteractionRecoveryPersistence.RecoveryTransitionResult transition
    ) {
        if (!transition.updated()) {
            throw new StaleRecoveryLeaseException();
        }
        if (transition.resultingStatus() == RecoveryStatus.EXHAUSTED) {
            observability.runtimeExecution(RuntimeExecutionStatus.REVIEW_REQUIRED);
            record(RecoveryOutcome.EXHAUSTED);
            return RecoveryStatus.EXHAUSTED;
        }
        if (transition.resultingStatus() == RecoveryStatus.MANUAL_REVIEW) {
            recordManualReview();
            return RecoveryStatus.MANUAL_REVIEW;
        }
        record(RecoveryOutcome.RESCHEDULED);
        return transition.resultingStatus();
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
