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
        meterRegistry.counter("adp.runtime.execution.total", "status", status.name()).increment();
    }

    public void idempotency(IdempotencyOutcome outcome) {
        meterRegistry.counter("adp.idempotency.resolution.total", "outcome", outcome.name()).increment();
    }

    public void recovery(RecoveryOutcome outcome) {
        meterRegistry.counter("adp.recovery.processing.total", "outcome", outcome.name()).increment();
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
