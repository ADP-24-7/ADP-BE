package com.adp.gateway.recovery.application;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;

import com.adp.gateway.connector.domain.ConnectorStatus;
import com.adp.gateway.recovery.domain.ExternalStatusQueryResult;
import org.springframework.stereotype.Service;

@Service
public class ExternalInteractionRecoveryService {

    private static final Duration LEASE_DURATION = Duration.ofSeconds(30);
    private static final Duration RETRY_DELAY = Duration.ofMinutes(1);

    private final ExternalInteractionRecoveryPersistence persistence;
    private final ExternalStatusQueryResolver statusQueryResolver;
    private final Clock clock;

    public ExternalInteractionRecoveryService(
        ExternalInteractionRecoveryPersistence persistence,
        ExternalStatusQueryResolver statusQueryResolver,
        Clock clock
    ) {
        this.persistence = persistence;
        this.statusQueryResolver = statusQueryResolver;
        this.clock = clock;
    }

    public boolean processNext(String workerId) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        return persistence.claimNext(workerId, now, LEASE_DURATION)
            .map(recovery -> {
                try {
                    ExternalStatusQueryResult queryResult = statusQueryResolver
                        .resolve(recovery.connectorId())
                        .query(recovery);
                    ConnectorStatus status = queryResult.status();
                    if (status == ConnectorStatus.ACKNOWLEDGED || status == ConnectorStatus.COMPLETED) {
                        requireLease(persistence.reconcile(recovery.recoveryId(), workerId, queryResult, now));
                    } else if (status == ConnectorStatus.SENT_UNKNOWN) {
                        requireLease(persistence.reschedule(
                            recovery.recoveryId(), workerId, now.plus(RETRY_DELAY), "STILL_SENT_UNKNOWN"
                        ));
                    } else {
                        requireLease(persistence.markManualReview(
                            recovery.recoveryId(), workerId, "EXTERNAL_STATUS_" + status.name()
                        ));
                    }
                } catch (ExternalStatusQueryUnavailableException exception) {
                    requireLease(persistence.reschedule(
                        recovery.recoveryId(), workerId, now.plus(RETRY_DELAY), "STATUS_QUERY_UNAVAILABLE"
                    ));
                } catch (ExternalStatusQueryPermanentException exception) {
                    requireLease(persistence.markManualReview(
                        recovery.recoveryId(), workerId, "STATUS_QUERY_PERMANENT_FAILURE"
                    ));
                } catch (StaleRecoveryLeaseException exception) {
                    throw exception;
                } catch (RuntimeException exception) {
                    requireLease(persistence.reschedule(
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
}
