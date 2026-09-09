package com.adp.gateway.observability;

import static org.assertj.core.api.Assertions.assertThat;
import com.adp.gateway.observability.GatewayObservability.IdempotencyOutcome;
import com.adp.gateway.observability.GatewayObservability.RecoveryOutcome;
import com.adp.gateway.observability.GatewayObservability.AiEvaluationEvidenceOutcome;
import com.adp.gateway.runtime.domain.RuntimeExecutionStatus;
import com.adp.gateway.policy.domain.PolicyLifecycleStage;
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
        observability.aiEvaluationEvidence(AiEvaluationEvidenceOutcome.PERSISTENCE_FAILED);
        observability.policyLifecycle(PolicyLifecycleStage.ACTIVE);
        observability.currentSelection(GatewayObservability.CurrentSelectionOutcome.ROLLED_BACK);
        observability.security(GatewayObservability.SecurityOutcome.DESTINATION_REJECTED);

        assertThat(registry.get("adp.runtime.terminal.transition.total")
            .tag("status", "COMPLETED").counter().count())
            .isEqualTo(1);
        assertThat(registry.get("adp.ai.evaluation.evidence.total")
            .tag("outcome", "PERSISTENCE_FAILED").counter().count()).isEqualTo(1);
        assertThat(registry.get("adp.idempotency.resolution.total").tag("outcome", "REPLAY").counter().count())
            .isEqualTo(1);
        assertThat(registry.get("adp.recovery.processing.total").tag("outcome", "RECONCILED").counter().count())
            .isEqualTo(1);
        assertThat(registry.get("adp.policy.lifecycle.transition.total").tag("stage", "ACTIVE")
            .counter().count()).isEqualTo(1);
        assertThat(registry.get("adp.policy.current.selection.total").tag("outcome", "ROLLED_BACK")
            .counter().count()).isEqualTo(1);
        assertThat(registry.get("adp.security.control.total").tag("outcome", "DESTINATION_REJECTED")
            .counter().count()).isEqualTo(1);
    }

}
