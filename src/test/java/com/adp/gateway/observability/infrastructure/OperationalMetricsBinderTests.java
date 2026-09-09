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
import java.time.ZoneOffset;

import com.adp.gateway.observability.application.OperationsMonitoringPort;
import com.adp.gateway.observability.domain.OperationalMetricSnapshot;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

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
}
