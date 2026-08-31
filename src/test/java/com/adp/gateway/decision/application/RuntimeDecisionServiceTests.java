package com.adp.gateway.decision.application;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.OffsetDateTime;
import java.util.List;

import com.adp.gateway.common.error.ReasonCode;
import com.adp.gateway.decision.domain.FinalAction;
import com.adp.gateway.decision.domain.RuntimeAuthorizationResult;
import com.adp.gateway.policy.domain.AnalysisStatus;
import com.adp.gateway.policy.domain.ApplicabilityResult;
import com.adp.gateway.policy.domain.ArtifactDigest;
import com.adp.gateway.policy.domain.ArtifactReference;
import com.adp.gateway.policy.domain.PolicyApplicabilitySpec;
import com.adp.gateway.policy.domain.PolicyAction;
import com.adp.gateway.policy.domain.PolicyEvaluation;
import com.adp.gateway.policy.domain.PolicyLifecycleStage;
import com.adp.gateway.policy.domain.PolicySnapshot;
import com.adp.gateway.policy.domain.RuntimePolicyContext;
import com.adp.gateway.policy.domain.RuntimeBinding;
import com.adp.gateway.policy.domain.SourcePolicyEvaluationArtifactRef;
import com.adp.gateway.retrieval.domain.DataClass;
import org.junit.jupiter.api.Test;

class RuntimeDecisionServiceTests {

    private final RuntimeDecisionService service = new RuntimeDecisionService(new MonotonicDecisionCombiner());
    private final RuntimePolicyContext runtimeContext = runtimeContext("runtime-digest-1");

    @Test
    void createsReproducibleDecisionForSameSnapshotAndRuntimeContext() {
        PolicySnapshot snapshot = snapshot(PolicyAction.ALLOW);

        var first = service.decide(
            runtimeContext,
            snapshot,
            RuntimeAuthorizationResult.ALLOWED,
            ApplicabilityResult.APPLICABLE
        );
        var second = service.decide(
            runtimeContext,
            snapshot,
            RuntimeAuthorizationResult.ALLOWED,
            ApplicabilityResult.APPLICABLE
        );

        assertThat(first.decisionId()).isEqualTo(second.decisionId());
        assertThat(first.finalAction()).isEqualTo(FinalAction.ALLOW);
        assertThat(first.runtimeReasonCodes()).contains(ReasonCode.POLICY_ALLOW);
        assertThat(first.evidenceRefs()).containsExactly(ref("EV-1", "evidence", "v1"));
        assertThat(first.requiredControls()).containsExactly(ref("CONTROL-1", "control", "v1"));
    }

    @Test
    void changesDecisionIdentityWhenRuntimeContextChanges() {
        PolicySnapshot snapshot = snapshot(PolicyAction.ALLOW);

        var first = service.decide(
            runtimeContext("runtime-digest-1"),
            snapshot,
            RuntimeAuthorizationResult.ALLOWED,
            ApplicabilityResult.APPLICABLE
        );
        var second = service.decide(
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
            runtimeContext,
            snapshot(PolicyAction.ALLOW),
            RuntimeAuthorizationResult.DENIED,
            ApplicabilityResult.APPLICABLE
        );

        assertThat(decision.policyAction()).isEqualTo(PolicyAction.ALLOW);
        assertThat(decision.finalAction()).isEqualTo(FinalAction.BLOCK);
        assertThat(decision.runtimeReasonCodes()).contains(ReasonCode.RUNTIME_AUTHORIZATION_DENIED);
    }

    @Test
    void doesNotRelaxTransformPolicyActionToAllow() {
        var decision = service.decide(
            runtimeContext,
            snapshot(PolicyAction.TRANSFORM),
            RuntimeAuthorizationResult.ALLOWED,
            ApplicabilityResult.APPLICABLE
        );

        assertThat(decision.policyAction()).isEqualTo(PolicyAction.TRANSFORM);
        assertThat(decision.finalAction()).isEqualTo(FinalAction.TRANSFORM);
    }

    @Test
    void doesNotRelaxBlockPolicyActionToAllow() {
        var decision = service.decide(
            runtimeContext,
            snapshot(PolicyAction.BLOCK),
            RuntimeAuthorizationResult.ALLOWED,
            ApplicabilityResult.APPLICABLE
        );

        assertThat(decision.policyAction()).isEqualTo(PolicyAction.BLOCK);
        assertThat(decision.finalAction()).isEqualTo(FinalAction.BLOCK);
    }

    @Test
    void routesIncompleteApplicabilityToReviewWithoutDefaultAllow() {
        var decision = service.decide(
            runtimeContext,
            snapshot(PolicyAction.ALLOW),
            RuntimeAuthorizationResult.ALLOWED,
            ApplicabilityResult.INCOMPLETE
        );

        assertThat(decision.finalAction()).isEqualTo(FinalAction.REVIEW);
        assertThat(decision.runtimeReasonCodes()).contains(ReasonCode.POLICY_INCOMPLETE);
    }

    @Test
    void keepsSourceArtifactIdentitySeparateFromBeSnapshotIdentity() {
        SourcePolicyEvaluationArtifactRef sourceArtifact = sourceArtifact();
        PolicyEvaluation evaluation = new PolicyEvaluation(
            List.of(ref("POL-1", "policy", "da-v1")),
            List.of(ref("RULE-1", "rule", "da-v1")),
            List.of(ref("REQ-1", "requirement", "da-v1")),
            List.of(ref("EV-1", "evidence", "da-v1")),
            PolicyAction.ALLOW,
            List.of(ref("CONTROL-1", "control", "da-v1")),
            List.of(ref("VA-1", "validation_artifact", "da-v1")),
            applicabilitySpec()
        );

        PolicySnapshot snapshot = new PolicySnapshot(
            "be-runtime-policy/2026-08-28",
            "be-snapshot-digest",
            OffsetDateTime.parse("2026-08-28T00:00:00Z"),
            PolicyLifecycleStage.PROJECT_PROVISIONAL,
            sourceArtifact,
            evaluation
        );

        assertThat(snapshot.policyVersion()).isEqualTo("be-runtime-policy/2026-08-28");
        assertThat(snapshot.snapshotDigest()).isEqualTo("be-snapshot-digest");
        assertThat(snapshot.sourcePolicyEvaluationArtifactRef()).isEqualTo(sourceArtifact);
    }

    private RuntimePolicyContext runtimeContext(String digest) {
        return new RuntimePolicyContext(
            "customer_summary",
            "CUSTOMER_SUPPORT",
            "customer",
            "subject-digest",
            "canonical-digest",
            List.of(DataClass.CUSTOMER_IDENTIFIER),
            List.of("SUPPORT_LOOKUP", "third_party_or_outsourcing"),
            "internal",
            digest
        );
    }

    private PolicySnapshot snapshot(PolicyAction policyAction) {
        SourcePolicyEvaluationArtifactRef artifact = sourceArtifact();
        PolicyEvaluation evaluation = new PolicyEvaluation(
            List.of(ref("POL-1", "policy", "v1")),
            List.of(ref("RULE-1", "rule", "v1")),
            List.of(ref("REQ-1", "requirement", "v1")),
            List.of(ref("EV-1", "evidence", "v1")),
            policyAction,
            List.of(ref("CONTROL-1", "control", "v1")),
            List.of(ref("VA-1", "validation_artifact", "v1")),
            applicabilitySpec()
        );
        return new PolicySnapshot(
            "be-runtime-policy/0.0.0",
            "be-snapshot-digest",
            OffsetDateTime.parse("2026-08-28T00:00:00Z"),
            PolicyLifecycleStage.PROJECT_PROVISIONAL,
            artifact,
            evaluation
        );
    }

    private SourcePolicyEvaluationArtifactRef sourceArtifact() {
        return new SourcePolicyEvaluationArtifactRef(
            "POLICY_EVALUATION_ARTIFACT",
            "da-artifact-v1",
            new ArtifactDigest("sha256", "da-artifact-digest")
        );
    }

    private ArtifactReference ref(String refId, String refType, String version) {
        return new ArtifactReference(refId, refType, version);
    }

    private PolicyApplicabilitySpec applicabilitySpec() {
        return new PolicyApplicabilitySpec(
            AnalysisStatus.VALIDATED,
            AnalysisStatus.VALIDATED,
            "customer support lookup",
            List.of(),
            List.of("SUPPORT_LOOKUP"),
            List.of("PERSONAL_INFORMATION"),
            new RuntimeBinding(
                "mapped",
                "CUSTOMER_IDENTIFIER",
                "customer_summary",
                "CUSTOMER_SUPPORT",
                "BINDING-1"
            )
        );
    }
}
