package com.adp.gateway.recovery.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.Optional;

import com.adp.gateway.connector.domain.ConnectorStatus;
import com.adp.gateway.recovery.domain.ExternalInteractionRecovery;
import com.adp.gateway.recovery.domain.RecoveryStatus;
import com.adp.gateway.recovery.domain.RetryDisposition;
import com.adp.gateway.recovery.domain.ExternalStatusQueryResult;
import com.adp.gateway.recovery.domain.ExternalRetryResult;
import com.adp.gateway.observability.GatewayObservability;
import com.adp.gateway.observability.GatewayObservability.RecoveryOutcome;
import org.junit.jupiter.api.Test;

class ExternalInteractionRecoveryServiceTests {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-03T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void unavailableStatusQueryReschedulesWithoutResending() {
        ExternalInteractionRecoveryPersistence persistence = mock(ExternalInteractionRecoveryPersistence.class);
        ExternalStatusQueryPort statusQuery = mock(ExternalStatusQueryPort.class);
        ExternalStatusQueryResolver resolver = mock(ExternalStatusQueryResolver.class);
        ExternalInteractionRecovery recovery = recovery();
        when(persistence.claimNext(eq("worker-1"), any(), any())).thenReturn(claimed(recovery));
        when(resolver.resolve(recovery.connectorId())).thenReturn(statusQuery);
        when(statusQuery.query(recovery)).thenThrow(new ExternalStatusQueryUnavailableException());
        when(persistence.reschedule(any(), any(), any(), any())).thenReturn(
            new ExternalInteractionRecoveryPersistence.RecoveryTransitionResult(
                true, RecoveryStatus.RETRY_SCHEDULED
            )
        );

        boolean processed = service(persistence, resolver, mock(ExternalRetryResolver.class),
            mock(GatewayObservability.class)).processNext("worker-1");

        assertThat(processed).isTrue();
        verify(persistence).reschedule(
            recovery.recoveryId(),
            "worker-1",
            OffsetDateTime.parse("2026-09-03T00:01:00Z"),
            "STATUS_QUERY_UNAVAILABLE"
        );
    }

    @Test
    void acknowledgedExternalStateCompletesReconciliation() {
        ExternalInteractionRecoveryPersistence persistence = mock(ExternalInteractionRecoveryPersistence.class);
        ExternalStatusQueryPort statusQuery = mock(ExternalStatusQueryPort.class);
        ExternalStatusQueryResolver resolver = mock(ExternalStatusQueryResolver.class);
        ExternalInteractionRecovery recovery = recovery();
        when(persistence.claimNext(eq("worker-1"), any(), any())).thenReturn(claimed(recovery));
        when(resolver.resolve(recovery.connectorId())).thenReturn(statusQuery);
        ExternalStatusQueryResult result = new ExternalStatusQueryResult(
            ConnectorStatus.ACKNOWLEDGED, "a".repeat(64)
        );
        when(statusQuery.query(recovery)).thenReturn(result);
        when(persistence.reconcile(any(), any(), any(), any())).thenReturn(true);

        service(persistence, resolver, mock(ExternalRetryResolver.class),
            mock(GatewayObservability.class)).processNext("worker-1");

        verify(persistence).reconcile(
            recovery.recoveryId(), "worker-1", result, OffsetDateTime.parse("2026-09-03T00:00:00Z")
        );
    }

    @Test
    void exhaustedRescheduleRecordsExhaustedAndReviewRequired() {
        ExternalInteractionRecoveryPersistence persistence = mock(ExternalInteractionRecoveryPersistence.class);
        ExternalStatusQueryPort statusQuery = mock(ExternalStatusQueryPort.class);
        ExternalStatusQueryResolver resolver = mock(ExternalStatusQueryResolver.class);
        GatewayObservability observability = mock(GatewayObservability.class);
        ExternalInteractionRecovery recovery = recovery();
        when(persistence.claimNext(eq("worker-1"), any(), any())).thenReturn(claimed(recovery));
        when(resolver.resolve(recovery.connectorId())).thenReturn(statusQuery);
        when(statusQuery.query(recovery)).thenThrow(new ExternalStatusQueryUnavailableException());
        when(persistence.reschedule(any(), any(), any(), any())).thenReturn(
            new ExternalInteractionRecoveryPersistence.RecoveryTransitionResult(true, RecoveryStatus.EXHAUSTED)
        );

        service(persistence, resolver, mock(ExternalRetryResolver.class), observability)
            .processNext("worker-1");

        verify(observability).recovery(RecoveryOutcome.EXHAUSTED);
        verify(observability).runtimeExecution(
            com.adp.gateway.runtime.domain.RuntimeExecutionStatus.REVIEW_REQUIRED
        );
    }

    @Test
    void expiredClaimsRecordExhaustedTransitionsEvenWithoutClaimedJob() {
        ExternalInteractionRecoveryPersistence persistence = mock(ExternalInteractionRecoveryPersistence.class);
        ExternalStatusQueryResolver resolver = mock(ExternalStatusQueryResolver.class);
        GatewayObservability observability = mock(GatewayObservability.class);
        when(persistence.claimNext(eq("worker-1"), any(), any())).thenReturn(
            new ExternalInteractionRecoveryPersistence.RecoveryClaimResult(Optional.empty(), 2)
        );

        boolean processed = service(
            persistence, resolver, mock(ExternalRetryResolver.class), observability
        )
            .processNext("worker-1");

        assertThat(processed).isFalse();
        verify(observability).recovery(RecoveryOutcome.EXHAUSTED, 2);
        verify(observability).runtimeExecution(
            com.adp.gateway.runtime.domain.RuntimeExecutionStatus.REVIEW_REQUIRED, 2
        );
    }

    @Test
    void sentUnknownIsRescheduledWithoutBlindRetry() {
        ExternalInteractionRecoveryPersistence persistence = mock(ExternalInteractionRecoveryPersistence.class);
        ExternalStatusQueryPort statusQuery = mock(ExternalStatusQueryPort.class);
        ExternalStatusQueryResolver statusResolver = mock(ExternalStatusQueryResolver.class);
        ExternalRetryResolver retryResolver = mock(ExternalRetryResolver.class);
        ExternalInteractionRecovery recovery = recovery();
        ExternalStatusQueryResult result = new ExternalStatusQueryResult(
            ConnectorStatus.SENT_UNKNOWN, "b".repeat(64)
        );
        when(persistence.claimNext(eq("worker-1"), any(), any())).thenReturn(claimed(recovery));
        when(statusResolver.resolve(recovery.connectorId())).thenReturn(statusQuery);
        when(statusQuery.query(recovery)).thenReturn(result);
        when(persistence.recordObservedAndReschedule(any(), any(), any(), any(), any(), any()))
            .thenReturn(new ExternalInteractionRecoveryPersistence.RecoveryTransitionResult(
                true, RecoveryStatus.RETRY_SCHEDULED
            ));

        service(persistence, statusResolver, retryResolver, mock(GatewayObservability.class))
            .processNext("worker-1");

        verify(retryResolver, never()).resolve(any());
        verify(persistence).recordObservedAndReschedule(
            recovery.recoveryId(), "worker-1", result, RetryDisposition.RECONCILE_FIRST,
            OffsetDateTime.parse("2026-09-03T00:01:00Z"), "STILL_SENT_UNKNOWN"
        );
    }

    @Test
    void retriesOnlyAfterProviderConfirmsNotSent() {
        ExternalInteractionRecoveryPersistence persistence = mock(ExternalInteractionRecoveryPersistence.class);
        ExternalStatusQueryPort statusQuery = mock(ExternalStatusQueryPort.class);
        ExternalStatusQueryResolver statusResolver = mock(ExternalStatusQueryResolver.class);
        ExternalRetryPort retryPort = mock(ExternalRetryPort.class);
        ExternalRetryResolver retryResolver = mock(ExternalRetryResolver.class);
        ExternalInteractionRecovery recovery = recovery();
        when(persistence.claimNext(eq("worker-1"), any(), any())).thenReturn(claimed(recovery));
        when(statusResolver.resolve(recovery.connectorId())).thenReturn(statusQuery);
        when(statusQuery.query(recovery)).thenReturn(new ExternalStatusQueryResult(
            ConnectorStatus.NOT_SENT, "c".repeat(64)
        ));
        when(retryResolver.resolve(recovery.connectorId())).thenReturn(retryPort);
        when(retryPort.retry(recovery)).thenReturn(new ExternalRetryResult(
            ConnectorStatus.ACKNOWLEDGED, "d".repeat(64)
        ));
        when(persistence.reconcile(any(), any(), any(), any())).thenReturn(true);

        service(persistence, statusResolver, retryResolver, mock(GatewayObservability.class))
            .processNext("worker-1");

        var ordered = org.mockito.Mockito.inOrder(statusQuery, retryPort);
        ordered.verify(statusQuery).query(recovery);
        ordered.verify(retryPort).retry(recovery);
        verify(persistence).reconcile(
            recovery.recoveryId(), "worker-1",
            new ExternalStatusQueryResult(ConnectorStatus.ACKNOWLEDGED, "d".repeat(64)),
            OffsetDateTime.parse("2026-09-03T00:00:00Z")
        );
    }

    private ExternalInteractionRecoveryService service(
        ExternalInteractionRecoveryPersistence persistence,
        ExternalStatusQueryResolver statusResolver,
        ExternalRetryResolver retryResolver,
        GatewayObservability observability
    ) {
        return new ExternalInteractionRecoveryService(
            persistence, statusResolver, retryResolver,
            new ExternalReconciliationEvidenceResolver(java.util.List.of()),
            new RecoveryBackoffPolicy(Duration.ofMinutes(1), Duration.ofMinutes(15)),
            Duration.ofSeconds(30),
            CLOCK, observability
        );
    }

    private ExternalInteractionRecoveryPersistence.RecoveryClaimResult claimed(
        ExternalInteractionRecovery recovery
    ) {
        return new ExternalInteractionRecoveryPersistence.RecoveryClaimResult(Optional.of(recovery), 0);
    }

    private ExternalInteractionRecovery recovery() {
        return new ExternalInteractionRecovery(
            "rec-1", "exec-1", "con-1", "connector-1", "preq-1",
            ConnectorStatus.SENT_UNKNOWN, null,
            RecoveryStatus.CLAIMED, RetryDisposition.RECONCILE_FIRST, 1, 5,
            OffsetDateTime.parse("2026-09-03T00:00:00Z"), "worker-1",
            OffsetDateTime.parse("2026-09-03T00:00:30Z"), null, null, null
        );
    }
}
