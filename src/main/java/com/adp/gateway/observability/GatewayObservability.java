package com.adp.gateway.observability;

import com.adp.gateway.runtime.domain.RuntimeExecutionStatus;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class GatewayObservability {

    private final MeterRegistry meterRegistry;

    public GatewayObservability(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void runtimeExecution(RuntimeExecutionStatus status) {
        runtimeExecution(status, 1);
    }

    public void runtimeExecution(RuntimeExecutionStatus status, int count) {
        increment("adp.runtime.terminal.transition.total", "status", status.name(), count);
    }

    public void idempotency(IdempotencyOutcome outcome) {
        meterRegistry.counter("adp.idempotency.resolution.total", "outcome", outcome.name()).increment();
    }

    public void recovery(RecoveryOutcome outcome) {
        recovery(outcome, 1);
    }

    public void recovery(RecoveryOutcome outcome, int count) {
        increment("adp.recovery.processing.total", "outcome", outcome.name(), count);
    }

    private void increment(String name, String tagName, String tagValue, int count) {
        if (count < 1) {
            throw new IllegalArgumentException("Metric increment count must be positive");
        }
        meterRegistry.counter(name, tagName, tagValue).increment(count);
    }

    public enum IdempotencyOutcome {
        NEW,
        REPLAY,
        CONFLICT,
        IN_PROGRESS
    }

    public enum RecoveryOutcome {
        RECONCILED,
        RESCHEDULED,
        EXHAUSTED,
        MANUAL_REVIEW,
        STALE_LEASE
    }
}
