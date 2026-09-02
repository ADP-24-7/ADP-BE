package com.adp.gateway.policyharness.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

import com.adp.gateway.auth.domain.AdpRole;
import com.adp.gateway.auth.domain.AuthPrincipal;
import com.adp.gateway.auth.domain.PrincipalType;
import com.adp.gateway.context.application.CanonicalValueHasher;
import com.adp.gateway.decision.domain.FinalAction;
import com.adp.gateway.decision.domain.RuntimeDecision;
import com.adp.gateway.egress.domain.DestinationBinding;
import com.adp.gateway.egress.domain.DestinationProfile;
import com.adp.gateway.egress.domain.ExecutionPackType;
import com.adp.gateway.policy.domain.ArtifactDigest;
import com.adp.gateway.policy.domain.PolicySnapshot;
import com.adp.gateway.policy.domain.SourcePolicyEvaluationArtifactRef;
import com.adp.gateway.policyharness.domain.ApprovalReuseStatus;
import com.adp.gateway.policyharness.domain.ApprovalScope;
import com.adp.gateway.policyharness.domain.FieldLineage;
import org.junit.jupiter.api.Test;

class PolicyHarnessEvaluatorTests {

    private final CanonicalValueHasher hasher = new CanonicalValueHasher();
    private final PolicyHarnessEvaluator evaluator = new PolicyHarnessEvaluator(hasher);

    @Test
    void blocksWhenAReleasedFieldFallsOutsideThePinnedApprovalScope() {
        RuntimeDecision decision = mock(RuntimeDecision.class);
        when(decision.finalAction()).thenReturn(FinalAction.ALLOW);
        PolicySnapshot snapshot = mock(PolicySnapshot.class);
        when(snapshot.sourcePolicyEvaluationArtifactRef()).thenReturn(new SourcePolicyEvaluationArtifactRef(
            "artifact",
            "v1",
            new ArtifactDigest("sha256", "artifact-digest")
        ));
        when(snapshot.policyVersion()).thenReturn("policy-v1");
        when(snapshot.snapshotDigest()).thenReturn("snapshot-digest");

        var result = evaluator.evaluate(
            approval(),
            "institution_local",
            principal(),
            "customer_summary",
            "CUSTOMER_SUPPORT",
            "subject-digest",
            List.of("AI_USE"),
            destination(),
            snapshot,
            decision,
            lineage(Set.of("request.prompt", "request.unapproved")),
            OffsetDateTime.parse("2026-09-02T00:00:00Z")
        );

        assertThat(result.approvalReuseStatus()).isEqualTo(ApprovalReuseStatus.BLOCKED);
        assertThat(result.reasonCodes()).contains("RELEASED_FIELD_SCOPE_MISMATCH");
        assertThat(result.policyLayers()).extracting("layer")
            .containsExactly("INSTITUTION_POLICY", "WORKLOAD_POLICY", "DESTINATION_PROFILE");
    }

    @Test
    void blocksWhenSubjectOrPinnedPolicySnapshotDoesNotMatch() {
        RuntimeDecision decision = mock(RuntimeDecision.class);
        when(decision.finalAction()).thenReturn(FinalAction.ALLOW);
        PolicySnapshot snapshot = snapshot("policy-v2", "snapshot-v2");

        var result = evaluator.evaluate(
            approval(),
            "institution_local",
            principal(),
            "customer_summary",
            "CUSTOMER_SUPPORT",
            "another-subject-digest",
            List.of("AI_USE"),
            destination(),
            snapshot,
            decision,
            lineage(Set.of("request.prompt")),
            OffsetDateTime.parse("2026-09-02T00:00:00Z")
        );

        assertThat(result.approvalReuseStatus()).isEqualTo(ApprovalReuseStatus.BLOCKED);
        assertThat(result.reasonCodes()).containsExactlyInAnyOrder(
            "SUBJECT_SCOPE_MISMATCH",
            "POLICY_VERSION_SCOPE_MISMATCH",
            "POLICY_SNAPSHOT_SCOPE_MISMATCH"
        );
    }

    @Test
    void finalBlockActionAlwaysProducesBlockedApprovalStatus() {
        RuntimeDecision decision = mock(RuntimeDecision.class);
        when(decision.finalAction()).thenReturn(FinalAction.BLOCK);

        var result = evaluator.evaluate(
            approval(),
            "institution_local",
            principal(),
            "customer_summary",
            "CUSTOMER_SUPPORT",
            "subject-digest",
            List.of("AI_USE"),
            destination(),
            snapshot("policy-v1", "snapshot-digest"),
            decision,
            lineage(Set.of("request.prompt")),
            OffsetDateTime.parse("2026-09-02T00:00:00Z")
        );

        assertThat(result.approvalReuseStatus()).isEqualTo(ApprovalReuseStatus.BLOCKED);
    }

    private ApprovalScope approval() {
        return new ApprovalScope(
            "approval-v1",
            "v1",
            "approval-digest",
            "institution_local",
            "institution-policy-v1",
            "institution-policy-digest",
            "customer_summary",
            "CUSTOMER_SUPPORT",
            "EXACT_DIGEST",
            "subject-digest",
            "policy-v1",
            "snapshot-digest",
            Set.of(AdpRole.RUNTIME_EXECUTOR),
            Set.of("AI_USE"),
            Set.of("request.prompt"),
            "dest-ai",
            "v1",
            OffsetDateTime.parse("2026-01-01T00:00:00Z"),
            null,
            List.of("evidence-v1")
        );
    }

    private PolicySnapshot snapshot(String policyVersion, String snapshotDigest) {
        PolicySnapshot snapshot = mock(PolicySnapshot.class);
        when(snapshot.sourcePolicyEvaluationArtifactRef()).thenReturn(new SourcePolicyEvaluationArtifactRef(
            "artifact",
            "v1",
            new ArtifactDigest("sha256", "artifact-digest")
        ));
        when(snapshot.policyVersion()).thenReturn(policyVersion);
        when(snapshot.snapshotDigest()).thenReturn(snapshotDigest);
        return snapshot;
    }

    private AuthPrincipal principal() {
        return new AuthPrincipal(
            "principal",
            PrincipalType.SERVICE,
            "Runtime",
            "institution_local",
            false,
            Set.of("customer_summary"),
            Set.of(AdpRole.RUNTIME_EXECUTOR)
        );
    }

    private DestinationProfile destination() {
        return new DestinationProfile(
            "dest-ai",
            "v1",
            "destination-digest",
            "contract-v1",
            "provider-ai",
            ExecutionPackType.AI,
            "schema-v1",
            "ACTIVE",
            OffsetDateTime.parse("2026-01-01T00:00:00Z"),
            null,
            List.of(new DestinationBinding("customer_summary", "CUSTOMER_SUPPORT")),
            List.of()
        );
    }

    private FieldLineage lineage(Set<String> released) {
        return new FieldLineage(
            Set.of("request.prompt"),
            Set.of("request.prompt"),
            Set.of("request.prompt"),
            released,
            hasher.hash("requested"),
            hasher.hash("retrieved"),
            hasher.hash("transformed"),
            hasher.hash("released")
        );
    }
}
