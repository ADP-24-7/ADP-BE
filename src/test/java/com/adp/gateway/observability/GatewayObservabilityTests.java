package com.adp.gateway.observability;

import static org.assertj.core.api.Assertions.assertThat;
import com.adp.gateway.observability.GatewayObservability.IdempotencyOutcome;
import com.adp.gateway.observability.GatewayObservability.RecoveryOutcome;
import com.adp.gateway.runtime.domain.RuntimeExecutionStatus;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class GatewayObservabilityTests {

    @Test
    void recordsOnlyDefinedLowCardinalityOutcomes() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        GatewayObservability observability = new GatewayObservability(registry);

        observability.runtimeExecution(RuntimeExecutionStatus.COMPLETED);
        observability.idempotency(IdempotencyOutcome.REPLAY);
        observability.recovery(RecoveryOutcome.RECONCILED);

        assertThat(registry.get("adp.runtime.execution.total").tag("status", "COMPLETED").counter().count())
            .isEqualTo(1);
        assertThat(registry.get("adp.idempotency.resolution.total").tag("outcome", "REPLAY").counter().count())
            .isEqualTo(1);
        assertThat(registry.get("adp.recovery.processing.total").tag("outcome", "RECONCILED").counter().count())
            .isEqualTo(1);
    }

}
