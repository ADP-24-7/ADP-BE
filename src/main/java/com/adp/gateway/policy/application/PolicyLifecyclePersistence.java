package com.adp.gateway.policy.application;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Set;

import com.adp.gateway.policy.domain.PolicyLifecycleRecord;
import com.adp.gateway.policy.domain.PolicyLifecycleStage;
import com.adp.gateway.policy.domain.PolicyLifecycleTransitionReason;
import com.adp.gateway.policy.domain.PolicyApprovalEvidenceBinding;
import com.adp.gateway.policy.domain.PolicyCurrentSelection;
import com.adp.gateway.egress.domain.ExecutionPackType;
import com.adp.gateway.policy.domain.PolicyLayer;

public interface PolicyLifecyclePersistence {
    PolicyLifecycleRecord create(PolicyLifecycleRecord record);

    PolicyLifecycleRecord load(
        String institutionId,
        Set<String> allowedWorkloads,
        String artifactId,
        String artifactVersion
    );

    PolicyLifecycleRecord transition(
        PolicyLifecycleRecord current,
        PolicyLifecycleStage target,
        String actorId,
        PolicyLifecycleTransitionReason reason,
        OffsetDateTime occurredAt
    );

    PolicyLifecycleRecord approve(
        PolicyLifecycleRecord current,
        String actorId,
        OffsetDateTime occurredAt,
        PolicyApprovalEvidenceBinding evidenceBinding
    );

    PolicyCurrentSelection activate(
        PolicyLifecycleRecord expectedArtifact,
        long expectedSelectionRevision,
        String actorId,
        OffsetDateTime occurredAt
    );

    PolicyCurrentSelection rollback(
        PolicyLifecycleRecord expectedTarget,
        long expectedSelectionRevision,
        String actorId,
        OffsetDateTime occurredAt
    );

    Optional<PolicyCurrentSelection> findCurrentSelection(
        String institutionId,
        ExecutionPackType executionPack,
        String workloadId,
        String purposeCode
    );

    PolicyLifecycleRecord loadActive(
        String institutionId,
        Set<String> allowedWorkloads,
        PolicyLayer policyLayer,
        ExecutionPackType executionPack,
        String workloadId,
        String purposeCode
    );

    void revalidateShadowInputs(
        PolicyLifecycleRecord expectedCandidate,
        PolicyLifecycleRecord expectedBaseline,
        Set<String> allowedWorkloads
    );
}
