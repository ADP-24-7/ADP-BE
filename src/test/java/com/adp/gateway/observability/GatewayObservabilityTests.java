package com.adp.gateway.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class GatewayObservabilityTests {

    @Test
    void recordsOnlyDefinedLowCardinalityOutcomes() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        GatewayObservability observability = new GatewayObservability(registry);

        observability.runtimeSubmission("CREATED", Duration.ofMillis(10));
        observability.idempotency("REPLAYED");
        observability.recovery("RECONCILED");

        assertThat(registry.get("adp.runtime.submission.total").tag("outcome", "CREATED").counter().count())
            .isEqualTo(1);
        assertThat(registry.get("adp.idempotency.resolution.total").tag("outcome", "REPLAYED").counter().count())
            .isEqualTo(1);
        assertThat(registry.get("adp.recovery.processing.total").tag("outcome", "RECONCILED").counter().count())
            .isEqualTo(1);
    }

    @Test
    void rejectsUnboundedOutcomeValues() {
        GatewayObservability observability = new GatewayObservability(new SimpleMeterRegistry());

        assertThatThrownBy(() -> observability.idempotency("customer-100"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
