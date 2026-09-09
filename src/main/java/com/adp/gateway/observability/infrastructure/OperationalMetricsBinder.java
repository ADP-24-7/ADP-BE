package com.adp.gateway.observability.infrastructure;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;

import com.adp.gateway.observability.application.OperationsMonitoringPort;
import com.adp.gateway.observability.domain.OperationalMetricSnapshot;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

@Component
public class OperationalMetricsBinder implements MeterBinder {

    private static final Logger log = LoggerFactory.getLogger(OperationalMetricsBinder.class);

    private final OperationsMonitoringPort port;
    private final Clock clock;
    private final Duration cacheDuration;
    private volatile OperationalMetricSnapshot snapshot = OperationalMetricSnapshot.empty();
    private volatile OffsetDateTime refreshAfter = OffsetDateTime.MIN;

    public OperationalMetricsBinder(
        OperationsMonitoringPort port,
        Clock clock,
        @Value("${adp.observability.operational-metrics-cache:5s}") Duration cacheDuration
    ) {
        if (cacheDuration.isNegative() || cacheDuration.isZero()) {
            throw new IllegalArgumentException("Operational metrics cache duration must be positive");
        }
        this.port = port;
        this.clock = clock;
        this.cacheDuration = cacheDuration;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        gauge(registry, "adp.recovery.queue.depth", "Current recoverable incident backlog",
            value -> value.recoveryBacklog());
        gauge(registry, "adp.recovery.queue.oldest.age.seconds", "Age of the oldest recovery backlog item",
            value -> value.recoveryOldestAgeSeconds());
        gauge(registry, "adp.recovery.manual.review.count", "Current recovery incidents requiring manual review",
            value -> value.recoveryManualReview());
        gauge(registry, "adp.recovery.exhausted.count", "Current exhausted recovery incidents",
            value -> value.recoveryExhausted());
        gauge(registry, "adp.policy.current.selection.count", "Current policy selections",
            value -> value.policyCurrentSelections());
        gauge(registry, "adp.policy.drift.count", "Current selections that differ from authoritative lifecycle state",
            value -> value.policyDriftedSelections());
    }

    private void gauge(
        MeterRegistry registry,
        String name,
        String description,
        java.util.function.ToDoubleFunction<OperationalMetricSnapshot> value
    ) {
        Gauge.builder(name, this, binder -> value.applyAsDouble(binder.currentSnapshot()))
            .description(description)
            .register(registry);
    }

    private OperationalMetricSnapshot currentSnapshot() {
        OffsetDateTime now = OffsetDateTime.now(clock);
        if (now.isBefore(refreshAfter)) {
            return snapshot;
        }
        synchronized (this) {
            now = OffsetDateTime.now(clock);
            if (now.isBefore(refreshAfter)) {
                return snapshot;
            }
            try {
                snapshot = port.loadGlobalMetricSnapshot(now);
            } catch (DataAccessException exception) {
                log.atWarn()
                    .addKeyValue("event", "operational_metrics_refresh")
                    .addKeyValue("outcome", "FAILED")
                    .log("Operational metrics refresh failed; serving the previous snapshot");
            }
            refreshAfter = now.plus(cacheDuration);
            return snapshot;
        }
    }
}
