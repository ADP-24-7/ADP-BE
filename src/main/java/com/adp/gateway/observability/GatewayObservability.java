package com.adp.gateway.observability;

import java.time.Duration;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class GatewayObservability {

    private final MeterRegistry meterRegistry;

    public GatewayObservability(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void runtimeSubmission(String outcome, Duration duration) {
        meterRegistry.counter("adp.runtime.submission.total", "outcome", allowed(
            outcome, "CREATED", "REPLAYED", "REJECTED", "FAILED"
        )).increment();
        meterRegistry.timer("adp.runtime.submission.duration", "outcome", outcome)
            .record(duration);
    }

    public void idempotency(String outcome) {
        meterRegistry.counter("adp.idempotency.resolution.total", "outcome", allowed(
            outcome, "CREATED", "REPLAYED", "CONFLICT", "IN_PROGRESS"
        )).increment();
    }

    public void recovery(String outcome) {
        meterRegistry.counter("adp.recovery.processing.total", "outcome", allowed(
            outcome, "NO_JOB", "RECONCILED", "RESCHEDULED", "MANUAL_REVIEW", "STALE_LEASE", "FAILED"
        )).increment();
    }

    private String allowed(String value, String... allowedValues) {
        for (String allowedValue : allowedValues) {
            if (allowedValue.equals(value)) {
                return value;
            }
        }
        throw new IllegalArgumentException("Unsupported low-cardinality metric value: " + value);
    }
}
