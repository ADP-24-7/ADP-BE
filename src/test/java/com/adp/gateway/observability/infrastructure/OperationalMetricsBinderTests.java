package com.adp.gateway.observability.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import com.adp.gateway.observability.application.OperationsMonitoringPort;
import com.adp.gateway.observability.domain.OperationalMetricSnapshot;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

class OperationalMetricsBinderTests {

    @Test
    void servesAllGaugesFromOneCachedDatabaseSnapshot() {
        OperationsMonitoringPort port = mock(OperationsMonitoringPort.class);
        Clock clock = Clock.fixed(Instant.parse("2026-09-09T00:00:00Z"), ZoneOffset.UTC);
        when(port.loadGlobalMetricSnapshot(OffsetDateTime.now(clock)))
            .thenReturn(new OperationalMetricSnapshot(4, 120, 2, 1, 3, 1));
        var registry = new SimpleMeterRegistry();
        new OperationalMetricsBinder(port, clock, Duration.ofSeconds(5)).bindTo(registry);

        assertThat(registry.get("adp.recovery.queue.depth").gauge().value()).isEqualTo(4);
        assertThat(registry.get("adp.recovery.queue.oldest.age.seconds").gauge().value()).isEqualTo(120);
        assertThat(registry.get("adp.policy.drift.count").gauge().value()).isEqualTo(1);
        verify(port, times(1)).loadGlobalMetricSnapshot(OffsetDateTime.now(clock));
    }

    @Test
    void exposesFailedRefreshAndAgeWhileServingTheLastSuccessfulSnapshot() {
        OperationsMonitoringPort port = mock(OperationsMonitoringPort.class);
        MutableClock clock = new MutableClock(Instant.parse("2026-09-09T00:00:00Z"));
        when(port.loadGlobalMetricSnapshot(org.mockito.ArgumentMatchers.any()))
            .thenReturn(new OperationalMetricSnapshot(4, 120, 2, 1, 3, 1))
            .thenThrow(new DataAccessResourceFailureException("database unavailable"));
        var registry = new SimpleMeterRegistry();
        new OperationalMetricsBinder(port, clock, Duration.ofMinutes(5)).bindTo(registry);

        assertThat(registry.get("adp.recovery.queue.depth").gauge().value()).isEqualTo(4);
        assertThat(registry.get("adp.operational.metrics.refresh.success").gauge().value()).isEqualTo(1);
        assertThat(registry.get("adp.operational.metrics.snapshot.age.seconds").gauge().value()).isZero();

        clock.advance(Duration.ofMinutes(6));
        assertThat(registry.get("adp.recovery.queue.depth").gauge().value()).isEqualTo(4);
        assertThat(registry.get("adp.operational.metrics.refresh.success").gauge().value()).isZero();
        assertThat(registry.get("adp.operational.metrics.snapshot.age.seconds").gauge().value()).isEqualTo(360);
        assertThat(registry.get("adp.operational.metrics.refresh.failures").functionCounter().count()).isEqualTo(1);
        verify(port, times(2)).loadGlobalMetricSnapshot(org.mockito.ArgumentMatchers.any());
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
