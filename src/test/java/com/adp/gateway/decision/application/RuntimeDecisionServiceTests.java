package com.adp.gateway.decision.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.util.List;

import com.adp.gateway.common.contract.RuntimeRequestContext;
import com.adp.gateway.common.error.ReasonCode;
import com.adp.gateway.decision.domain.DecisionAction;
import com.adp.gateway.decision.domain.RuntimeAuthorizationResult;
import com.adp.gateway.policy.domain.ApplicabilityResult;
import com.adp.gateway.policy.domain.PolicyArtifact;
import com.adp.gateway.policy.domain.PolicyEvaluation;
import com.adp.gateway.policy.domain.PolicyLifecycleStage;
import com.adp.gateway.policy.domain.PolicySnapshot;
import com.adp.gateway.policy.domain.RuntimePolicyContext;
import com.adp.gateway.retrieval.domain.DataClass;
import org.junit.jupiter.api.Test;

class RuntimeDecisionServiceTests {

    private final RuntimeDecisionService service = new RuntimeDecisionService(new MonotonicDecisionCombiner());
    private final RuntimeRequestContext context = new RuntimeRequestContext(
        "req_decision",
        "trace_decision",
        "idem_decision",
        "customer_summary",
        "CUSTOMER_SUPPORT",
        "customer:customer-100"
    );
    private final RuntimePolicyContext runtimeContext = runtimeContext("runtime-digest-1");

    @Test
    void createsReproducibleDecisionForSameSnapshotAndRuntimeContext() {
        PolicySnapshot snapshot = snapshot(DecisionAction.ALLOW);

        var first = service.decide(
            context,
            runtimeContext,
            snapshot,
            RuntimeAuthorizationResult.ALLOWED,
            ApplicabilityResult.APPLICABLE
        );
        var second = service.decide(
            context,
            runtimeContext,
            snapshot,
            RuntimeAuthorizationResult.ALLOWED,
            ApplicabilityResult.APPLICABLE
        );

        assertThat(first.decisionId()).isEqualTo(second.decisionId());
        assertThat(first.finalAction()).isEqualTo(DecisionAction.ALLOW);
        assertThat(first.runtimeReasonCodes()).contains(ReasonCode.POLICY_ALLOW);
        assertThat(first.evidenceRefs()).containsExactly("EV-1");
        assertThat(first.requiredControls()).containsExactly("CONTROL-1");
    }

    @Test
    void changesDecisionIdentityWhenRuntimeContextChanges() {
        PolicySnapshot snapshot = snapshot(DecisionAction.ALLOW);

        var first = service.decide(
            context,
            runtimeContext("runtime-digest-1"),
            snapshot,
            RuntimeAuthorizationResult.ALLOWED,
            ApplicabilityResult.APPLICABLE
        );
        var second = service.decide(
            context,
            runtimeContext("runtime-digest-2"),
            snapshot,
            RuntimeAuthorizationResult.ALLOWED,
            ApplicabilityResult.APPLICABLE
        );

        assertThat(first.decisionId()).isNotEqualTo(second.decisionId());
    }

    @Test
    void blocksWhenRuntimeAuthorizationIsDeniedEvenIfPolicyAllows() {
        var decision = service.decide(
            context,
            runtimeContext,
            snapshot(DecisionAction.ALLOW),
            RuntimeAuthorizationResult.DENIED,
            ApplicabilityResult.APPLICABLE
        );

        assertThat(decision.policyAction()).isEqualTo(DecisionAction.ALLOW);
        assertThat(decision.finalAction()).isEqualTo(DecisionAction.BLOCK);
        assertThat(decision.runtimeReasonCodes()).contains(ReasonCode.RUNTIME_AUTHORIZATION_DENIED);
    }

    @Test
    void doesNotRelaxTransformPolicyActionToAllow() {
        var decision = service.decide(
            context,
            runtimeContext,
            snapshot(DecisionAction.TRANSFORM),
            RuntimeAuthorizationResult.ALLOWED,
            ApplicabilityResult.APPLICABLE
        );

        assertThat(decision.policyAction()).isEqualTo(DecisionAction.TRANSFORM);
        assertThat(decision.finalAction()).isEqualTo(DecisionAction.TRANSFORM);
    }

    @Test
    void doesNotRelaxBlockPolicyActionToAllow() {
        var decision = service.decide(
            context,
            runtimeContext,
            snapshot(DecisionAction.BLOCK),
            RuntimeAuthorizationResult.ALLOWED,
            ApplicabilityResult.APPLICABLE
        );

        assertThat(decision.policyAction()).isEqualTo(DecisionAction.BLOCK);
        assertThat(decision.finalAction()).isEqualTo(DecisionAction.BLOCK);
    }

    @Test
    void routesIncompleteApplicabilityToReviewWithoutDefaultAllow() {
        var decision = service.decide(
            context,
            runtimeContext,
            snapshot(DecisionAction.ALLOW),
            RuntimeAuthorizationResult.ALLOWED,
            ApplicabilityResult.INCOMPLETE
        );

        assertThat(decision.finalAction()).isEqualTo(DecisionAction.REVIEW);
        assertThat(decision.runtimeReasonCodes()).contains(ReasonCode.POLICY_INCOMPLETE);
    }

    @Test
    void rejectsSnapshotWhenEvaluationDigestDoesNotMatchSourceArtifact() {
        PolicyArtifact artifact = new PolicyArtifact(
            "PROJECT_PROVISIONAL",
            "0.0.0",
            PolicyLifecycleStage.PROJECT_PROVISIONAL,
            "customer_summary",
            List.of(),
            "snapshot-digest"
        );
        PolicyEvaluation evaluation = new PolicyEvaluation(
            List.of("RULE-1"),
            List.of("EV-1"),
            DecisionAction.ALLOW,
            List.of("CONTROL-1"),
            artifact.artifactVersion(),
            "different-digest"
        );

        assertThatThrownBy(() -> new PolicySnapshot(
            artifact.artifactVersion(),
            artifact.digest(),
            OffsetDateTime.parse("2026-08-28T00:00:00Z"),
            artifact.status(),
            artifact,
            evaluation
        )).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("digests must match");
    }

    private RuntimePolicyContext runtimeContext(String digest) {
        return new RuntimePolicyContext(
            "customer_summary",
            "CUSTOMER_SUPPORT",
            "customer",
            "subject-digest",
            "canonical-digest",
            List.of(DataClass.CUSTOMER_IDENTIFIER),
            "SUPPORT_LOOKUP",
            "internal",
            digest
        );
    }

    private PolicySnapshot snapshot(DecisionAction policyAction) {
        PolicyArtifact artifact = new PolicyArtifact(
            "PROJECT_PROVISIONAL",
            "0.0.0",
            PolicyLifecycleStage.PROJECT_PROVISIONAL,
            "customer_summary",
            List.of(),
            "snapshot-digest"
        );
        PolicyEvaluation evaluation = new PolicyEvaluation(
            List.of("RULE-1"),
            List.of("EV-1"),
            policyAction,
            List.of("CONTROL-1"),
            artifact.artifactVersion(),
            artifact.digest()
        );
        return new PolicySnapshot(
            artifact.artifactVersion(),
            artifact.digest(),
            OffsetDateTime.parse("2026-08-28T00:00:00Z"),
            artifact.status(),
            artifact,
            evaluation
        );
    }
}
