package com.adp.gateway.policy.application;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Set;

import com.adp.gateway.auth.domain.AdpRole;
import com.adp.gateway.auth.domain.AuthPrincipal;
import com.adp.gateway.egress.domain.ExecutionPackType;
import com.adp.gateway.policy.domain.PolicyLayer;
import com.adp.gateway.policy.domain.PolicyLifecycleRecord;
import com.adp.gateway.policy.domain.PolicyLifecycleStage;
import com.adp.gateway.policy.domain.PolicyLifecycleTransitionReason;
import com.adp.gateway.policy.domain.PolicyApprovalEvidenceBinding;
import com.adp.gateway.policy.domain.PolicyCurrentSelection;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PolicyLifecycleService {
    private static final Set<ExecutionPackType> LIFECYCLE_PACKS = Set.of(
        ExecutionPackType.COMMON, ExecutionPackType.AI, ExecutionPackType.DIGITAL_ASSET
    );
    private static final Set<PolicyLifecycleStage> PRIVILEGED_TARGETS = Set.of(
        PolicyLifecycleStage.APPROVED, PolicyLifecycleStage.ACTIVE,
        PolicyLifecycleStage.SUPERSEDED, PolicyLifecycleStage.REVIEW,
        PolicyLifecycleStage.ROLLED_BACK
    );
    private final PolicyLifecyclePersistence persistence;
    private final PolicyLifecycleTransitionValidator transitionValidator;
    private final PolicyShadowEvidencePersistence shadowEvidencePersistence;
    private final PolicyShadowApprovalPolicy shadowApprovalPolicy;
    private final Clock clock;

    public PolicyLifecycleService(
        PolicyLifecyclePersistence persistence,
        PolicyLifecycleTransitionValidator transitionValidator,
        PolicyShadowEvidencePersistence shadowEvidencePersistence,
        PolicyShadowApprovalPolicy shadowApprovalPolicy,
        Clock clock
    ) {
        this.persistence = persistence;
        this.transitionValidator = transitionValidator;
        this.shadowEvidencePersistence = shadowEvidencePersistence;
        this.shadowApprovalPolicy = shadowApprovalPolicy;
        this.clock = clock;
    }

    @Transactional
    public PolicyLifecycleRecord create(
        AuthPrincipal principal,
        String artifactId,
        String artifactVersion,
        String artifactDigest,
        PolicyLayer layer,
        ExecutionPackType pack,
        String workloadId,
        String purposeCode
    ) {
        requireRole(principal, AdpRole.OPERATOR);
        requireScope(principal, workloadId);
        validateIdentity(artifactId, artifactVersion, artifactDigest, workloadId, purposeCode);
        if (!LIFECYCLE_PACKS.contains(pack)) {
            throw new PolicyLifecycleException("POLICY_LIFECYCLE_ARTIFACT_INVALID");
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        return persistence.create(new PolicyLifecycleRecord(
            artifactId, artifactVersion, artifactDigest, principal.institutionId(), layer, pack,
            workloadId, purposeCode, PolicyLifecycleStage.DRAFT, principal.principalId(), 0, now, now
        ));
    }

    @Transactional
    public PolicyLifecycleRecord transition(
        AuthPrincipal principal,
        String artifactId,
        String artifactVersion,
        PolicyLifecycleStage target,
        PolicyLifecycleTransitionReason reason
    ) {
        return transition(principal, artifactId, artifactVersion, target, reason, false);
    }

    @Transactional
    public PolicyLifecycleRecord transitionForRuntimeSelection(
        AuthPrincipal principal,
        String artifactId,
        String artifactVersion,
        PolicyLifecycleStage target,
        PolicyLifecycleTransitionReason reason
    ) {
        return transition(principal, artifactId, artifactVersion, target, reason, true);
    }

    private PolicyLifecycleRecord transition(
        AuthPrincipal principal,
        String artifactId,
        String artifactVersion,
        PolicyLifecycleStage target,
        PolicyLifecycleTransitionReason reason,
        boolean runtimeSelection
    ) {
        PolicyLifecycleRecord current = loadScoped(principal, artifactId, artifactVersion);
        boolean selectionTransition = target == PolicyLifecycleStage.ACTIVE
            || target == PolicyLifecycleStage.SUPERSEDED
            || target == PolicyLifecycleStage.REVIEW
            || target == PolicyLifecycleStage.ROLLED_BACK;
        boolean supportedDigitalAssetTransition = runtimeSelection
            && current.executionPack() == ExecutionPackType.DIGITAL_ASSET
            && (target == PolicyLifecycleStage.ACTIVE || target == PolicyLifecycleStage.SUPERSEDED);
        if ((selectionTransition && !supportedDigitalAssetTransition)
            || (runtimeSelection && !supportedDigitalAssetTransition)) {
            throw new PolicyLifecycleException("POLICY_LIFECYCLE_TRANSITION_INVALID");
        }
        requireRole(principal, PRIVILEGED_TARGETS.contains(target) ? AdpRole.PRIVILEGED_OPERATOR : AdpRole.OPERATOR);
        if ((target == PolicyLifecycleStage.APPROVED || target == PolicyLifecycleStage.ACTIVE)
            && current.createdBy().equals(principal.principalId())) {
            throw new PolicyLifecycleException("POLICY_LIFECYCLE_MAKER_CHECKER_VIOLATION");
        }
        if (reason == null) {
            throw new PolicyLifecycleException("POLICY_LIFECYCLE_REASON_INVALID");
        }
        if (target == PolicyLifecycleStage.APPROVED) {
            throw new PolicyLifecycleException("POLICY_SHADOW_APPROVAL_REQUIRED");
        }
        transitionValidator.validate(current.lifecycleStage(), target, reason);
        return persistence.transition(current, target, principal.principalId(), reason, OffsetDateTime.now(clock));
    }

    @Transactional
    public PolicyLifecycleRecord approve(
        AuthPrincipal principal,
        String artifactId,
        String artifactVersion,
        String shadowEvaluationId
    ) {
        PolicyLifecycleRecord current = loadScoped(principal, artifactId, artifactVersion);
        requireRole(principal, AdpRole.PRIVILEGED_OPERATOR);
        if (current.createdBy().equals(principal.principalId())) {
            throw new PolicyLifecycleException("POLICY_LIFECYCLE_MAKER_CHECKER_VIOLATION");
        }
        if (shadowEvaluationId == null || shadowEvaluationId.isBlank() || shadowEvaluationId.length() > 80) {
            throw new PolicyLifecycleException("POLICY_SHADOW_APPROVAL_EVIDENCE_NOT_FOUND");
        }
        transitionValidator.validate(
            current.lifecycleStage(), PolicyLifecycleStage.APPROVED,
            PolicyLifecycleTransitionReason.APPROVAL_GRANTED
        );
        var evidence = shadowEvidencePersistence.loadLatestForApproval(
            current.institutionId(), principal.workloadIds(), current.artifactId(), current.artifactVersion(),
            shadowEvaluationId
        );
        shadowApprovalPolicy.validate(evidence);
        return persistence.approve(
            current, principal.principalId(), OffsetDateTime.now(clock),
            PolicyApprovalEvidenceBinding.from(evidence, PolicyShadowApprovalPolicy.VERSION)
        );
    }

    @Transactional
    public PolicyCurrentSelection activate(
        AuthPrincipal principal,
        String artifactId,
        String artifactVersion,
        long expectedArtifactRevision,
        long expectedSelectionRevision
    ) {
        PolicyLifecycleRecord current = loadScoped(principal, artifactId, artifactVersion);
        requireRole(principal, AdpRole.PRIVILEGED_OPERATOR);
        if (current.executionPack() == ExecutionPackType.DIGITAL_ASSET) {
            throw new PolicyLifecycleException("POLICY_LIFECYCLE_TRANSITION_INVALID");
        }
        if (current.createdBy().equals(principal.principalId())) {
            throw new PolicyLifecycleException("POLICY_LIFECYCLE_MAKER_CHECKER_VIOLATION");
        }
        if (current.lifecycleStage() != PolicyLifecycleStage.APPROVED) {
            throw new PolicyLifecycleException("POLICY_LIFECYCLE_TRANSITION_INVALID");
        }
        if (current.revision() != expectedArtifactRevision) {
            throw new PolicyLifecycleException("POLICY_LIFECYCLE_CONCURRENT_MODIFICATION");
        }
        return persistence.activate(
            current, expectedSelectionRevision, principal.principalId(), OffsetDateTime.now(clock)
        );
    }

    @Transactional
    public PolicyCurrentSelection rollback(
        AuthPrincipal principal,
        String artifactId,
        String artifactVersion,
        long expectedTargetRevision,
        long expectedSelectionRevision
    ) {
        PolicyLifecycleRecord target = loadScoped(principal, artifactId, artifactVersion);
        requireRole(principal, AdpRole.PRIVILEGED_OPERATOR);
        if (target.executionPack() == ExecutionPackType.DIGITAL_ASSET) {
            throw new PolicyLifecycleException("POLICY_LIFECYCLE_TRANSITION_INVALID");
        }
        if (target.createdBy().equals(principal.principalId())) {
            throw new PolicyLifecycleException("POLICY_LIFECYCLE_MAKER_CHECKER_VIOLATION");
        }
        if (target.revision() != expectedTargetRevision) {
            throw new PolicyLifecycleException("POLICY_LIFECYCLE_CONCURRENT_MODIFICATION");
        }
        return persistence.rollback(
            target, expectedSelectionRevision, principal.principalId(), OffsetDateTime.now(clock)
        );
    }

    public PolicyCurrentSelection loadCurrentSelection(
        AuthPrincipal principal,
        ExecutionPackType executionPack,
        String workloadId,
        String purposeCode
    ) {
        if (principal == null || principal.institutionId() == null || principal.institutionId().isBlank()
            || (!principal.hasRole(AdpRole.OPERATOR) && !principal.hasRole(AdpRole.PRIVILEGED_OPERATOR)
                && !principal.hasRole(AdpRole.AUDITOR))) {
            throw new PolicyLifecycleException("POLICY_LIFECYCLE_FORBIDDEN");
        }
        requireScope(principal, workloadId);
        return persistence.findCurrentSelection(
            principal.institutionId(), executionPack, workloadId, purposeCode
        ).orElseThrow(() -> new PolicyLifecycleException("POLICY_CURRENT_SELECTION_NOT_FOUND"));
    }

    public PolicyLifecycleRecord load(AuthPrincipal principal, String artifactId, String artifactVersion) {
        if (!principal.hasRole(AdpRole.OPERATOR) && !principal.hasRole(AdpRole.PRIVILEGED_OPERATOR)
            && !principal.hasRole(AdpRole.AUDITOR)) {
            throw new PolicyLifecycleException("POLICY_LIFECYCLE_FORBIDDEN");
        }
        return loadScoped(principal, artifactId, artifactVersion);
    }

    private void validateIdentity(String id, String version, String digest, String workload, String purpose) {
        if (blank(id) || blank(version) || blank(workload) || blank(purpose)
            || digest == null || !digest.matches("[0-9a-f]{64}")) {
            throw new PolicyLifecycleException("POLICY_LIFECYCLE_ARTIFACT_INVALID");
        }
    }

    private PolicyLifecycleRecord loadScoped(AuthPrincipal principal, String artifactId, String artifactVersion) {
        if (principal.institutionId() == null || principal.institutionId().isBlank()) {
            throw new PolicyLifecycleException("POLICY_LIFECYCLE_FORBIDDEN");
        }
        return persistence.load(principal.institutionId(), principal.workloadIds(), artifactId, artifactVersion);
    }

    private void requireScope(AuthPrincipal principal, String workloadId) {
        if (!principal.canAccessWorkload(workloadId)) {
            throw new PolicyLifecycleException("POLICY_LIFECYCLE_FORBIDDEN");
        }
    }

    private void requireRole(AuthPrincipal principal, AdpRole role) {
        if (!principal.hasRole(role)) {
            throw new PolicyLifecycleException("POLICY_LIFECYCLE_FORBIDDEN");
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank() || value.length() > 120;
    }
}
