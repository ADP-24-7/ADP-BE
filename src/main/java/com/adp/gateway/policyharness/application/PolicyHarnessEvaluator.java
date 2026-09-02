package com.adp.gateway.policyharness.application;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.adp.gateway.auth.domain.AuthPrincipal;
import com.adp.gateway.context.application.CanonicalValueHasher;
import com.adp.gateway.decision.domain.FinalAction;
import com.adp.gateway.decision.domain.RuntimeDecision;
import com.adp.gateway.egress.domain.DestinationProfile;
import com.adp.gateway.policy.domain.PolicySnapshot;
import com.adp.gateway.policyharness.domain.ApprovalReuseStatus;
import com.adp.gateway.policyharness.domain.ApprovalScope;
import com.adp.gateway.policyharness.domain.FieldLineage;
import com.adp.gateway.policyharness.domain.PolicyHarnessBinding;
import com.adp.gateway.policyharness.domain.PolicyLayerReference;
import org.springframework.stereotype.Service;

@Service
public class PolicyHarnessEvaluator {

    private final CanonicalValueHasher hasher;

    public PolicyHarnessEvaluator(CanonicalValueHasher hasher) {
        this.hasher = hasher;
    }

    public PolicyHarnessBinding evaluate(
        ApprovalScope approval,
        String institutionId,
        AuthPrincipal principal,
        String workloadId,
        String purposeCode,
        String subjectRefDigest,
        List<String> processingContexts,
        DestinationProfile destinationProfile,
        PolicySnapshot policySnapshot,
        RuntimeDecision decision,
        FieldLineage fieldLineage,
        java.time.OffsetDateTime requestStartedAt
    ) {
        List<String> reasons = new ArrayList<>();
        if (!approval.isEffectiveAt(requestStartedAt)) {
            reasons.add("APPROVAL_NOT_EFFECTIVE");
        }
        if (!approval.institutionId().equals(institutionId)) {
            reasons.add("INSTITUTION_SCOPE_MISMATCH");
        }
        if (!approval.workloadId().equals(workloadId)) {
            reasons.add("WORKLOAD_SCOPE_MISMATCH");
        }
        if (!approval.purposeCode().equals(purposeCode)) {
            reasons.add("PURPOSE_SCOPE_MISMATCH");
        }
        if (!"EXACT_DIGEST".equals(approval.subjectScopeType())
            || !approval.subjectScopeDigest().equals(subjectRefDigest)) {
            reasons.add("SUBJECT_SCOPE_MISMATCH");
        }
        if (!approval.workloadPolicyVersion().equals(policySnapshot.policyVersion())) {
            reasons.add("POLICY_VERSION_SCOPE_MISMATCH");
        }
        if (!approval.workloadPolicySnapshotDigest().equals(policySnapshot.snapshotDigest())) {
            reasons.add("POLICY_SNAPSHOT_SCOPE_MISMATCH");
        }
        if (principal.roles().stream().noneMatch(approval.allowedRoles()::contains)) {
            reasons.add("ROLE_SCOPE_MISMATCH");
        }
        if (!approval.allowedProcessingContexts().containsAll(processingContexts)) {
            reasons.add("PROCESSING_CONTEXT_SCOPE_MISMATCH");
        }
        if (!approval.destinationProfileId().equals(destinationProfile.destinationProfileId())
            || !approval.destinationProfileVersion().equals(destinationProfile.profileVersion())) {
            reasons.add("DESTINATION_SCOPE_MISMATCH");
        }

        Set<String> requestedOutsideScope = outsideScope(fieldLineage.requestedFields(), approval.allowedFields());
        Set<String> retrievedOutsideScope = outsideScope(fieldLineage.retrievedFields(), approval.allowedFields());
        Set<String> releasedOutsideScope = outsideScope(fieldLineage.releasedFields(), approval.allowedFields());
        if (!requestedOutsideScope.isEmpty()) {
            reasons.add("REQUESTED_FIELD_SCOPE_MISMATCH");
        }
        if (!retrievedOutsideScope.isEmpty()) {
            reasons.add("RETRIEVED_FIELD_SCOPE_MISMATCH");
        }
        if (!releasedOutsideScope.isEmpty()) {
            reasons.add("RELEASED_FIELD_SCOPE_MISMATCH");
        }

        ApprovalReuseStatus status = status(reasons, decision.finalAction());
        List<PolicyLayerReference> layers = List.of(
            new PolicyLayerReference(
                "INSTITUTION_POLICY",
                approval.institutionId(),
                approval.institutionPolicyVersion(),
                approval.institutionPolicyDigest()
            ),
            new PolicyLayerReference(
                "WORKLOAD_POLICY",
                policySnapshot.sourcePolicyEvaluationArtifactRef().artifactId(),
                policySnapshot.policyVersion(),
                policySnapshot.snapshotDigest()
            ),
            new PolicyLayerReference(
                "DESTINATION_PROFILE",
                destinationProfile.destinationProfileId(),
                destinationProfile.profileVersion(),
                destinationProfile.profileDigest()
            )
        );
        return new PolicyHarnessBinding(
            institutionId,
            approval.approvalReference(),
            approval.approvalVersion(),
            approval.approvalScopeDigest(),
            status,
            reasons,
            layers,
            layersDigest(layers),
            fieldLineage
        );
    }

    private Set<String> outsideScope(Set<String> actual, Set<String> approved) {
        return actual.stream()
            .filter(field -> !approved.contains(field))
            .collect(Collectors.toUnmodifiableSet());
    }

    private ApprovalReuseStatus status(List<String> reasons, FinalAction finalAction) {
        if (finalAction == FinalAction.BLOCK) {
            return ApprovalReuseStatus.BLOCKED;
        }
        if (reasons.stream().anyMatch(reason -> reason.equals("APPROVAL_NOT_EFFECTIVE")
            || reason.equals("INSTITUTION_SCOPE_MISMATCH")
            || reason.equals("WORKLOAD_SCOPE_MISMATCH")
            || reason.equals("PURPOSE_SCOPE_MISMATCH")
            || reason.equals("SUBJECT_SCOPE_MISMATCH")
            || reason.equals("POLICY_VERSION_SCOPE_MISMATCH")
            || reason.equals("POLICY_SNAPSHOT_SCOPE_MISMATCH")
            || reason.equals("ROLE_SCOPE_MISMATCH")
            || reason.equals("PROCESSING_CONTEXT_SCOPE_MISMATCH")
            || reason.equals("DESTINATION_SCOPE_MISMATCH")
            || reason.equals("RELEASED_FIELD_SCOPE_MISMATCH"))) {
            return ApprovalReuseStatus.BLOCKED;
        }
        if (!reasons.isEmpty() || finalAction == FinalAction.REVIEW) {
            return ApprovalReuseStatus.REVIEW_REQUIRED;
        }
        return finalAction == FinalAction.TRANSFORM
            ? ApprovalReuseStatus.TRANSFORM_REQUIRED
            : ApprovalReuseStatus.REUSE_ALLOWED;
    }

    private String layersDigest(List<PolicyLayerReference> layers) {
        String canonical = layers.stream()
            .sorted(Comparator.comparing(PolicyLayerReference::layer))
            .map(layer -> String.join(":", layer.layer(), layer.referenceId(), layer.version(), layer.digest()))
            .collect(Collectors.joining("|"));
        return hasher.hash(canonical);
    }
}
