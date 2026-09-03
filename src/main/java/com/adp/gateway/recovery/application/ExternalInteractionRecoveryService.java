package com.adp.gateway.recovery.application;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;

import com.adp.gateway.connector.domain.ConnectorStatus;
import com.adp.gateway.recovery.domain.ExternalStatusQueryResult;
import com.adp.gateway.observability.GatewayObservability;
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
        boolean processed = persistence.claimNext(workerId, now, LEASE_DURATION)
            .map(recovery -> {
                try {
                    ExternalStatusQueryResult queryResult = statusQueryResolver
                        .resolve(recovery.connectorId())
                        .query(recovery);
                    ConnectorStatus status = queryResult.status();
                    if (status == ConnectorStatus.ACKNOWLEDGED || status == ConnectorStatus.COMPLETED) {
                        requireLease(persistence.reconcile(recovery.recoveryId(), workerId, queryResult, now));
                        record("RECONCILED");
                    } else if (status == ConnectorStatus.SENT_UNKNOWN) {
                        requireLease(persistence.reschedule(
                            recovery.recoveryId(), workerId, now.plus(RETRY_DELAY), "STILL_SENT_UNKNOWN"
                        ));
                        record("RESCHEDULED");
                    } else {
                        requireLease(persistence.markManualReview(
                            recovery.recoveryId(), workerId, "EXTERNAL_STATUS_" + status.name()
                        ));
                        record("MANUAL_REVIEW");
                    }
                } catch (ExternalStatusQueryUnavailableException exception) {
                    requireLease(persistence.reschedule(
                        recovery.recoveryId(), workerId, now.plus(RETRY_DELAY), "STATUS_QUERY_UNAVAILABLE"
                    ));
                    record("RESCHEDULED");
                } catch (ExternalStatusQueryPermanentException exception) {
                    requireLease(persistence.markManualReview(
                        recovery.recoveryId(), workerId, "STATUS_QUERY_PERMANENT_FAILURE"
                    ));
                    record("MANUAL_REVIEW");
                } catch (AmbiguousExternalStatusQueryAdapterException exception) {
                    requireLease(persistence.markManualReview(
                        recovery.recoveryId(), workerId, "STATUS_QUERY_ADAPTER_AMBIGUOUS"
                    ));
                    record("MANUAL_REVIEW");
                } catch (StaleRecoveryLeaseException exception) {
                    record("STALE_LEASE");
                    throw exception;
                } catch (RuntimeException exception) {
                    requireLease(persistence.reschedule(
                        recovery.recoveryId(), workerId, now.plus(RETRY_DELAY), "INTERNAL_QUERY_ERROR"
                    ));
                    record("RESCHEDULED");
                }
                return true;
            })
            .orElse(false);
        if (!processed) {
            record("NO_JOB");
        }
        return processed;
    }

    private void requireLease(boolean updated) {
        if (!updated) {
            throw new StaleRecoveryLeaseException();
        }
    }

    private void record(String outcome) {
        observability.recovery(outcome);
        log.info("recovery_processing outcome={}", outcome);
    }
}
