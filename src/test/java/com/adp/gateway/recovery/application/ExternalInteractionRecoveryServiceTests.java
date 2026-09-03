package com.adp.gateway.recovery.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import com.adp.gateway.connector.domain.ConnectorStatus;
import com.adp.gateway.recovery.domain.ExternalInteractionRecovery;
import com.adp.gateway.recovery.domain.RecoveryStatus;
import com.adp.gateway.recovery.domain.RetryDisposition;
import org.junit.jupiter.api.Test;

class ExternalInteractionRecoveryServiceTests {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-03T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void unavailableStatusQueryReschedulesWithoutResending() {
        ExternalInteractionRecoveryPersistence persistence = mock(ExternalInteractionRecoveryPersistence.class);
        ExternalStatusQueryPort statusQuery = mock(ExternalStatusQueryPort.class);
        ExternalStatusQueryResolver resolver = mock(ExternalStatusQueryResolver.class);
        ExternalInteractionRecovery recovery = recovery();
        when(persistence.claimNext(eq("worker-1"), any(), any())).thenReturn(Optional.of(recovery));
        when(resolver.resolve(recovery.connectorId())).thenReturn(statusQuery);
        when(statusQuery.query(recovery)).thenThrow(new ExternalStatusQueryUnavailableException());

        boolean processed = new ExternalInteractionRecoveryService(persistence, resolver, CLOCK)
            .processNext("worker-1");

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
        when(persistence.claimNext(eq("worker-1"), any(), any())).thenReturn(Optional.of(recovery));
        when(resolver.resolve(recovery.connectorId())).thenReturn(statusQuery);
        when(statusQuery.query(recovery)).thenReturn(ConnectorStatus.ACKNOWLEDGED);

        new ExternalInteractionRecoveryService(persistence, resolver, CLOCK).processNext("worker-1");

        verify(persistence).markReconciled(recovery.recoveryId(), "worker-1");
    }

    private ExternalInteractionRecovery recovery() {
        return new ExternalInteractionRecovery(
            "rec-1", "exec-1", "con-1", "connector-1", ConnectorStatus.SENT_UNKNOWN,
            RecoveryStatus.CLAIMED, RetryDisposition.RECONCILE_FIRST, 1, 5,
            OffsetDateTime.parse("2026-09-03T00:00:00Z"), "worker-1",
            OffsetDateTime.parse("2026-09-03T00:00:30Z"), null
        );
    }
}
