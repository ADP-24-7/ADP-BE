package com.adp.gateway.observability;

import com.adp.gateway.runtime.domain.RuntimeExecutionStatus;
import com.adp.gateway.policy.domain.PolicyLifecycleStage;
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

    public void aiEvaluationEvidence(AiEvaluationEvidenceOutcome outcome) {
        meterRegistry.counter("adp.ai.evaluation.evidence.total", "outcome", outcome.name()).increment();
    }

    public void aiEvaluationBundleExport(AiEvaluationBundleExportOutcome outcome) {
        meterRegistry.counter("adp.ai.evaluation.bundle.export.total", "outcome", outcome.name()).increment();
    }

    public void recovery(RecoveryOutcome outcome, int count) {
        increment("adp.recovery.processing.total", "outcome", outcome.name(), count);
    }

    public void policyLifecycle(PolicyLifecycleStage stage) {
        meterRegistry.counter("adp.policy.lifecycle.transition.total", "stage", stage.name()).increment();
    }

    public void currentSelection(CurrentSelectionOutcome outcome) {
        meterRegistry.counter("adp.policy.current.selection.total", "outcome", outcome.name()).increment();
    }

    public void security(SecurityOutcome outcome) {
        meterRegistry.counter("adp.security.control.total", "outcome", outcome.name()).increment();
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

    public enum CurrentSelectionOutcome {
        ACTIVATED,
        ROLLED_BACK
    }

    public enum SecurityOutcome {
        REQUEST_FRESHNESS_REJECTED,
        INSTITUTION_SCOPE_REJECTED,
        AUTHORIZATION_POLICY_REJECTED,
        DESTINATION_REJECTED
    }

    public enum AiEvaluationEvidenceOutcome {
        CONNECTOR_RECORDED,
        COMPLETE,
        PERSISTENCE_FAILED
    }

    public enum AiEvaluationBundleExportOutcome {
        SUCCESS,
        NOT_FOUND,
        INCOMPLETE,
        SIZE_LIMIT_EXCEEDED,
        PROVENANCE_MISMATCH,
        MODEL_MISMATCH
    }
}
