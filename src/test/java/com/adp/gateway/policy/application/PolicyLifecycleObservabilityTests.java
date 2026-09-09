package com.adp.gateway.policy.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Set;

import com.adp.gateway.auth.domain.AdpRole;
import com.adp.gateway.auth.domain.AuthPrincipal;
import com.adp.gateway.auth.domain.PrincipalType;
import com.adp.gateway.egress.domain.ExecutionPackType;
import com.adp.gateway.observability.GatewayObservability;
import com.adp.gateway.policy.domain.PolicyLayer;
import com.adp.gateway.policy.domain.PolicyLifecycleRecord;
import com.adp.gateway.policy.domain.PolicyLifecycleStage;
import com.adp.gateway.policy.domain.PolicyLifecycleTransitionReason;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class PolicyLifecycleObservabilityTests {

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void doesNotFailCommittedOperationWhenPostCommitMetricRecordingFails() {
        PolicyLifecyclePersistence persistence = mock(PolicyLifecyclePersistence.class);
        GatewayObservability observability = mock(GatewayObservability.class);
        OffsetDateTime now = OffsetDateTime.parse("2026-09-09T00:00:00Z");
        PolicyLifecycleRecord draft = record(PolicyLifecycleStage.DRAFT, 0, now);
        PolicyLifecycleRecord validated = record(PolicyLifecycleStage.VALIDATED, 1, now);
        when(persistence.load("institution-1", Set.of("workload-1"), "artifact-1", "1.0.0"))
            .thenReturn(draft);
        when(persistence.transition(any(), any(), any(), any(), any())).thenReturn(validated);
        doThrow(new IllegalStateException("meter registry unavailable"))
            .when(observability).policyLifecycle(PolicyLifecycleStage.VALIDATED);
        PolicyLifecycleService service = new PolicyLifecycleService(
            persistence,
            new PolicyLifecycleTransitionValidator(),
            mock(PolicyShadowEvidencePersistence.class),
            mock(PolicyShadowApprovalPolicy.class),
            observability,
            Clock.fixed(Instant.parse("2026-09-09T00:00:00Z"), ZoneOffset.UTC)
        );
        AuthPrincipal principal = new AuthPrincipal(
            "operator-1", PrincipalType.USER, "operator-1", "institution-1", false,
            Set.of("workload-1"), Set.of(AdpRole.OPERATOR)
        );

        TransactionSynchronizationManager.initSynchronization();
        PolicyLifecycleRecord result = service.transition(
            principal, "artifact-1", "1.0.0", PolicyLifecycleStage.VALIDATED,
            PolicyLifecycleTransitionReason.VALIDATION_PASSED
        );

        assertThat(result).isEqualTo(validated);
        assertThatCode(() -> TransactionSynchronizationManager.getSynchronizations()
            .forEach(synchronization -> synchronization.afterCommit()))
            .doesNotThrowAnyException();
    }

    private PolicyLifecycleRecord record(PolicyLifecycleStage stage, long revision, OffsetDateTime now) {
        return new PolicyLifecycleRecord(
            "artifact-1", "1.0.0", "a".repeat(64), "institution-1", PolicyLayer.WORKLOAD,
            ExecutionPackType.AI, "workload-1", "CUSTOMER_SUPPORT", stage, "maker-1", revision, now, now
        );
    }
}
