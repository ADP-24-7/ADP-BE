package com.adp.gateway.policy.infrastructure;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.adp.gateway.egress.domain.ExecutionPackType;
import com.adp.gateway.policy.application.PolicyLifecycleException;
import com.adp.gateway.policy.application.PolicyLifecyclePersistence;
import com.adp.gateway.policy.domain.PolicyLayer;
import com.adp.gateway.policy.domain.PolicyApprovalEvidenceBinding;
import com.adp.gateway.policy.domain.PolicyCurrentSelection;
import com.adp.gateway.policy.domain.PolicyLifecycleRecord;
import com.adp.gateway.policy.domain.PolicyLifecycleStage;
import com.adp.gateway.policy.domain.PolicyLifecycleTransitionReason;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class JdbcPolicyLifecyclePersistence implements PolicyLifecyclePersistence {
    private final JdbcClient jdbcClient;

    public JdbcPolicyLifecyclePersistence(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public PolicyLifecycleRecord create(PolicyLifecycleRecord record) {
        try {
            jdbcClient.sql("""
                insert into policy.lifecycle_artifact (
                    artifact_id, artifact_version, artifact_digest, institution_id, policy_layer,
                    execution_pack, workload_id, purpose_code, lifecycle_stage, created_by,
                    revision, created_at, updated_at
                ) values (
                    :artifactId, :artifactVersion, :artifactDigest, :institutionId, :policyLayer,
                    :executionPack, :workloadId, :purposeCode, :stage, :createdBy, 0, :createdAt, :updatedAt
                )
                """)
                .param("artifactId", record.artifactId())
                .param("artifactVersion", record.artifactVersion())
                .param("artifactDigest", record.artifactDigest())
                .param("institutionId", record.institutionId())
                .param("policyLayer", record.policyLayer().name())
                .param("executionPack", record.executionPack().name())
                .param("workloadId", record.workloadId())
                .param("purposeCode", record.purposeCode())
                .param("stage", record.lifecycleStage().name())
                .param("createdBy", record.createdBy())
                .param("createdAt", record.createdAt())
                .param("updatedAt", record.updatedAt())
                .update();
            return record;
        } catch (DuplicateKeyException exception) {
            throw new PolicyLifecycleException("POLICY_LIFECYCLE_ARTIFACT_CONFLICT");
        }
    }

    @Override
    public PolicyLifecycleRecord load(
        String institutionId,
        Set<String> allowedWorkloads,
        String artifactId,
        String artifactVersion
    ) {
        if (allowedWorkloads == null || allowedWorkloads.isEmpty()) {
            throw new PolicyLifecycleException("POLICY_LIFECYCLE_ARTIFACT_NOT_FOUND");
        }
        String workloadPredicate = allowedWorkloads.contains("*") ? "" : "and workload_id in (:allowedWorkloads)";
        var query = jdbcClient.sql("""
                select artifact_id, artifact_version, artifact_digest, institution_id, policy_layer,
                       execution_pack, workload_id, purpose_code, lifecycle_stage, created_by,
                       revision, created_at, updated_at
                from policy.lifecycle_artifact
                where institution_id = :institutionId
                  and artifact_id = :artifactId
                  and artifact_version = :artifactVersion
                """ + workloadPredicate)
            .param("institutionId", institutionId)
            .param("artifactId", artifactId)
            .param("artifactVersion", artifactVersion);
        if (!allowedWorkloads.contains("*")) {
            query = query.param("allowedWorkloads", allowedWorkloads);
        }
        return query.query((rs, rowNum) -> new PolicyLifecycleRecord(
                rs.getString("artifact_id"), rs.getString("artifact_version"), rs.getString("artifact_digest"),
                rs.getString("institution_id"), PolicyLayer.valueOf(rs.getString("policy_layer")),
                ExecutionPackType.valueOf(rs.getString("execution_pack")), rs.getString("workload_id"),
                rs.getString("purpose_code"), PolicyLifecycleStage.valueOf(rs.getString("lifecycle_stage")),
                rs.getString("created_by"), rs.getLong("revision"),
                rs.getObject("created_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class)
            ))
            .optional()
            .orElseThrow(() -> new PolicyLifecycleException("POLICY_LIFECYCLE_ARTIFACT_NOT_FOUND"));
    }

    @Override
    public PolicyLifecycleRecord transition(
        PolicyLifecycleRecord current,
        PolicyLifecycleStage target,
        String actorId,
        PolicyLifecycleTransitionReason reason,
        OffsetDateTime occurredAt
    ) {
        lockScope(current);
        int updated = jdbcClient.sql("""
                update policy.lifecycle_artifact
                set lifecycle_stage = :target, revision = revision + 1, updated_at = :occurredAt
                where institution_id = :institutionId
                  and artifact_id = :artifactId and artifact_version = :artifactVersion
                  and workload_id = :workloadId
                  and lifecycle_stage = :current and revision = :revision
                """)
            .param("target", target.name())
            .param("occurredAt", occurredAt)
            .param("institutionId", current.institutionId())
            .param("artifactId", current.artifactId())
            .param("artifactVersion", current.artifactVersion())
            .param("workloadId", current.workloadId())
            .param("current", current.lifecycleStage().name())
            .param("revision", current.revision())
            .update();
        if (updated != 1) {
            throw new PolicyLifecycleException("POLICY_LIFECYCLE_CONCURRENT_MODIFICATION");
        }
        jdbcClient.sql("""
                insert into policy.lifecycle_transition_event (
                    institution_id, artifact_id, artifact_version, from_stage, to_stage, actor_id,
                    reason_code, artifact_digest, occurred_at, approval_gate_version
                ) values (
                    :institutionId, :artifactId, :artifactVersion, :fromStage, :toStage, :actorId,
                    :reason, :artifactDigest, :occurredAt, 'NOT_APPLICABLE'
                )
                """)
            .param("artifactId", current.artifactId())
            .param("institutionId", current.institutionId())
            .param("artifactVersion", current.artifactVersion())
            .param("fromStage", current.lifecycleStage().name())
            .param("toStage", target.name())
            .param("actorId", actorId)
            .param("reason", reason.name())
            .param("artifactDigest", current.artifactDigest())
            .param("occurredAt", occurredAt)
            .update();
        return load(current.institutionId(), Set.of(current.workloadId()), current.artifactId(), current.artifactVersion());
    }

    @Override
    public PolicyLifecycleRecord approve(
        PolicyLifecycleRecord current,
        String actorId,
        OffsetDateTime occurredAt,
        PolicyApprovalEvidenceBinding binding
    ) {
        lockScope(current);
        PolicyLifecycleRecord authoritative = load(
            current.institutionId(), Set.of(current.workloadId()), current.artifactId(), current.artifactVersion()
        );
        PolicyLifecycleRecord active;
        try {
            active = loadActive(
                current.institutionId(), Set.of(current.workloadId()), current.policyLayer(), current.executionPack(),
                current.workloadId(), current.purposeCode()
            );
        } catch (PolicyLifecycleException exception) {
            throw new PolicyLifecycleException("POLICY_SHADOW_APPROVAL_EVIDENCE_STALE");
        }
        if (!sameCurrentApprovalCandidate(current, authoritative)
            || binding.candidateRevision() + 1 != authoritative.revision()
            || !Objects.equals(binding.candidateArtifactDigest(), authoritative.artifactDigest())
            || !sameApprovalBaseline(binding, active)) {
            throw new PolicyLifecycleException("POLICY_SHADOW_APPROVAL_EVIDENCE_STALE");
        }

        int updated = jdbcClient.sql("""
                update policy.lifecycle_artifact
                set lifecycle_stage = 'APPROVED', revision = revision + 1, updated_at = :occurredAt
                where institution_id = :institutionId
                  and artifact_id = :artifactId and artifact_version = :artifactVersion
                  and workload_id = :workloadId
                  and lifecycle_stage = 'SHADOW' and revision = :revision
                """)
            .param("occurredAt", occurredAt)
            .param("institutionId", current.institutionId())
            .param("artifactId", current.artifactId())
            .param("artifactVersion", current.artifactVersion())
            .param("workloadId", current.workloadId())
            .param("revision", current.revision())
            .update();
        if (updated != 1) {
            throw new PolicyLifecycleException("POLICY_SHADOW_APPROVAL_EVIDENCE_STALE");
        }
        jdbcClient.sql("""
                insert into policy.lifecycle_transition_event (
                    institution_id, artifact_id, artifact_version, from_stage, to_stage, actor_id,
                    reason_code, artifact_digest, occurred_at, approval_gate_version,
                    shadow_evaluation_id, shadow_candidate_revision,
                    shadow_baseline_artifact_id, shadow_baseline_artifact_version,
                    shadow_baseline_artifact_digest, shadow_evaluation_case_id,
                    shadow_evaluation_case_version, shadow_result, approval_policy_version
                ) values (
                    :institutionId, :artifactId, :artifactVersion, 'SHADOW', 'APPROVED', :actorId,
                    'APPROVAL_GRANTED', :artifactDigest, :occurredAt, 'SHADOW_EVIDENCE_V1',
                    :shadowEvaluationId, :candidateRevision,
                    :baselineId, :baselineVersion, :baselineDigest, :caseId, :caseVersion, :shadowResult,
                    :approvalPolicyVersion
                )
                """)
            .param("institutionId", current.institutionId())
            .param("artifactId", current.artifactId())
            .param("artifactVersion", current.artifactVersion())
            .param("actorId", actorId)
            .param("artifactDigest", current.artifactDigest())
            .param("occurredAt", occurredAt)
            .param("shadowEvaluationId", binding.shadowEvaluationId())
            .param("candidateRevision", binding.candidateRevision())
            .param("baselineId", binding.baselineArtifactId())
            .param("baselineVersion", binding.baselineArtifactVersion())
            .param("baselineDigest", binding.baselineArtifactDigest())
            .param("caseId", binding.evaluationCaseId())
            .param("caseVersion", binding.evaluationCaseVersion())
            .param("shadowResult", binding.shadowResult())
            .param("approvalPolicyVersion", binding.approvalPolicyVersion())
            .update();
        return load(current.institutionId(), Set.of(current.workloadId()), current.artifactId(), current.artifactVersion());
    }

    @Override
    public PolicyCurrentSelection activate(
        PolicyLifecycleRecord expectedArtifact,
        long expectedSelectionRevision,
        String actorId,
        OffsetDateTime occurredAt
    ) {
        lockCurrentSelectionScope(expectedArtifact);
        PolicyLifecycleRecord candidate = load(
            expectedArtifact.institutionId(), Set.of(expectedArtifact.workloadId()),
            expectedArtifact.artifactId(), expectedArtifact.artifactVersion()
        );
        if (!sameIdentityAndScope(expectedArtifact, candidate)
            || expectedArtifact.revision() != candidate.revision()
            || candidate.lifecycleStage() != PolicyLifecycleStage.APPROVED) {
            throw new PolicyLifecycleException("POLICY_LIFECYCLE_CONCURRENT_MODIFICATION");
        }

        Optional<PolicyCurrentSelection> currentSelection = findCurrentSelection(
            candidate.institutionId(), candidate.executionPack(), candidate.workloadId(), candidate.purposeCode()
        );
        if (currentSelection.map(PolicyCurrentSelection::selectionRevision).orElse(0L)
            != expectedSelectionRevision) {
            throw new PolicyLifecycleException("POLICY_LIFECYCLE_CONCURRENT_MODIFICATION");
        }
        Optional<PolicyLifecycleRecord> previous = currentSelection
            .map(selection -> loadSelectedLifecycle(selection, PolicyLifecycleStage.ACTIVE))
            .or(() -> findOnlyActive(candidate));
        if (previous.filter(value -> sameIdentityAndScope(value, candidate)).isPresent()) {
            throw new PolicyLifecycleException("POLICY_LIFECYCLE_TRANSITION_INVALID");
        }
        if (previous.isEmpty() || !approvalMatchesBaseline(candidate, previous.get())) {
            throw new PolicyLifecycleException("POLICY_CURRENT_SELECTION_APPROVAL_STALE");
        }
        previous.ifPresent(value -> transition(
            value, PolicyLifecycleStage.SUPERSEDED, actorId,
            PolicyLifecycleTransitionReason.ACTIVE_VERSION_SUPERSEDED, occurredAt
        ));
        PolicyLifecycleRecord activated = transition(
            candidate, PolicyLifecycleStage.ACTIVE, actorId,
            PolicyLifecycleTransitionReason.ACTIVATION_APPROVED, occurredAt
        );
        long selectionRevision = expectedSelectionRevision + 1;
        saveCurrentSelection(activated, selectionRevision, actorId, occurredAt);
        recordSelectionEvent("ACTIVATED", previous.orElse(null), activated, selectionRevision,
            actorId, PolicyLifecycleTransitionReason.ACTIVATION_APPROVED, occurredAt);
        return requireCurrentSelection(activated);
    }

    @Override
    public PolicyCurrentSelection rollback(
        PolicyLifecycleRecord expectedTarget,
        long expectedSelectionRevision,
        String actorId,
        OffsetDateTime occurredAt
    ) {
        lockCurrentSelectionScope(expectedTarget);
        PolicyLifecycleRecord target = load(
            expectedTarget.institutionId(), Set.of(expectedTarget.workloadId()),
            expectedTarget.artifactId(), expectedTarget.artifactVersion()
        );
        if (!sameIdentityAndScope(expectedTarget, target)
            || expectedTarget.revision() != target.revision()
            || target.lifecycleStage() != PolicyLifecycleStage.SUPERSEDED
            || !wasApprovedAndActivated(target)) {
            throw new PolicyLifecycleException("POLICY_ROLLBACK_TARGET_INVALID");
        }
        PolicyCurrentSelection selection = findCurrentSelection(
            target.institutionId(), target.executionPack(), target.workloadId(), target.purposeCode()
        ).orElseThrow(() -> new PolicyLifecycleException("POLICY_CURRENT_SELECTION_NOT_FOUND"));
        if (selection.selectionRevision() != expectedSelectionRevision) {
            throw new PolicyLifecycleException("POLICY_LIFECYCLE_CONCURRENT_MODIFICATION");
        }
        PolicyLifecycleRecord current = loadSelectedLifecycle(selection, PolicyLifecycleStage.ACTIVE);
        if (sameIdentityAndScope(current, target)) {
            throw new PolicyLifecycleException("POLICY_ROLLBACK_TARGET_INVALID");
        }

        transition(current, PolicyLifecycleStage.ROLLED_BACK, actorId,
            PolicyLifecycleTransitionReason.ROLLBACK_APPROVED, occurredAt);
        PolicyLifecycleRecord restored = transition(target, PolicyLifecycleStage.ACTIVE, actorId,
            PolicyLifecycleTransitionReason.ROLLBACK_RESTORED, occurredAt);
        long nextSelectionRevision = selection.selectionRevision() + 1;
        saveCurrentSelection(restored, nextSelectionRevision, actorId, occurredAt);
        recordSelectionEvent("ROLLED_BACK", current, restored, nextSelectionRevision,
            actorId, PolicyLifecycleTransitionReason.ROLLBACK_APPROVED, occurredAt);
        return requireCurrentSelection(restored);
    }

    @Override
    public Optional<PolicyCurrentSelection> findCurrentSelection(
        String institutionId,
        ExecutionPackType executionPack,
        String workloadId,
        String purposeCode
    ) {
        Optional<PolicyCurrentSelection> selection = jdbcClient.sql("""
                select institution_id, policy_layer, execution_pack, workload_id, purpose_code,
                       artifact_id, artifact_version, artifact_digest, artifact_revision,
                       selection_revision, selected_by, selected_at
                from policy.current_selection
                where institution_id = :institutionId
                  and execution_pack = :executionPack
                  and workload_id = :workloadId
                  and purpose_code = :purposeCode
                """)
            .param("institutionId", institutionId)
            .param("executionPack", executionPack.name())
            .param("workloadId", workloadId)
            .param("purposeCode", purposeCode)
            .query((rs, rowNum) -> new PolicyCurrentSelection(
                rs.getString("institution_id"), PolicyLayer.valueOf(rs.getString("policy_layer")),
                ExecutionPackType.valueOf(rs.getString("execution_pack")), rs.getString("workload_id"),
                rs.getString("purpose_code"), rs.getString("artifact_id"), rs.getString("artifact_version"),
                rs.getString("artifact_digest"), rs.getLong("artifact_revision"),
                rs.getLong("selection_revision"), rs.getString("selected_by"),
                rs.getObject("selected_at", OffsetDateTime.class)
            )).optional();
        selection.ifPresent(value -> loadSelectedLifecycle(value, PolicyLifecycleStage.ACTIVE));
        return selection;
    }

    @Override
    public PolicyLifecycleRecord loadActive(
        String institutionId,
        Set<String> allowedWorkloads,
        PolicyLayer policyLayer,
        ExecutionPackType executionPack,
        String workloadId,
        String purposeCode
    ) {
        if (allowedWorkloads == null || allowedWorkloads.isEmpty()
            || (!allowedWorkloads.contains("*") && !allowedWorkloads.contains(workloadId))) {
            throw new PolicyLifecycleException("POLICY_SHADOW_BASELINE_NOT_FOUND");
        }
        var candidates = jdbcClient.sql("""
                select artifact_id, artifact_version, artifact_digest, institution_id, policy_layer,
                       execution_pack, workload_id, purpose_code, lifecycle_stage, created_by,
                       revision, created_at, updated_at
                from policy.lifecycle_artifact
                where institution_id = :institutionId
                  and execution_pack = :executionPack
                  and policy_layer = :policyLayer
                  and workload_id = :workloadId
                  and purpose_code = :purposeCode
                  and lifecycle_stage = 'ACTIVE'
                order by updated_at desc, artifact_id, artifact_version
                limit 2
                """)
            .param("institutionId", institutionId)
            .param("executionPack", executionPack.name())
            .param("policyLayer", policyLayer.name())
            .param("workloadId", workloadId)
            .param("purposeCode", purposeCode)
            .query((rs, rowNum) -> new PolicyLifecycleRecord(
                rs.getString("artifact_id"), rs.getString("artifact_version"), rs.getString("artifact_digest"),
                rs.getString("institution_id"), PolicyLayer.valueOf(rs.getString("policy_layer")),
                ExecutionPackType.valueOf(rs.getString("execution_pack")), rs.getString("workload_id"),
                rs.getString("purpose_code"), PolicyLifecycleStage.valueOf(rs.getString("lifecycle_stage")),
                rs.getString("created_by"), rs.getLong("revision"),
                rs.getObject("created_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class)
            )).list();
        if (candidates.size() != 1) {
            throw new PolicyLifecycleException(
                candidates.isEmpty() ? "POLICY_SHADOW_BASELINE_NOT_FOUND" : "POLICY_SHADOW_BASELINE_AMBIGUOUS"
            );
        }
        return candidates.getFirst();
    }

    @Override
    public void revalidateShadowInputs(
        PolicyLifecycleRecord expectedCandidate,
        PolicyLifecycleRecord expectedBaseline,
        Set<String> allowedWorkloads
    ) {
        lockScope(expectedCandidate);
        try {
            PolicyLifecycleRecord candidate = load(
                expectedCandidate.institutionId(), allowedWorkloads,
                expectedCandidate.artifactId(), expectedCandidate.artifactVersion()
            );
            PolicyLifecycleRecord baseline = loadActive(
                expectedCandidate.institutionId(), allowedWorkloads,
                expectedCandidate.policyLayer(), expectedCandidate.executionPack(),
                expectedCandidate.workloadId(), expectedCandidate.purposeCode()
            );
            if (!sameCandidate(expectedCandidate, candidate) || !sameBaseline(expectedBaseline, baseline)) {
                throw new PolicyLifecycleException("POLICY_SHADOW_STALE_EVALUATION");
            }
        } catch (PolicyLifecycleException exception) {
            if ("POLICY_SHADOW_STALE_EVALUATION".equals(exception.reasonCode())) {
                throw exception;
            }
            throw new PolicyLifecycleException("POLICY_SHADOW_STALE_EVALUATION");
        }
    }

    private void lockScope(PolicyLifecycleRecord record) {
        String scope = String.join("|",
            record.institutionId(), record.policyLayer().name(), record.executionPack().name(),
            record.workloadId(), record.purposeCode()
        );
        jdbcClient.sql("select pg_advisory_xact_lock(hashtextextended(:scope, 0))")
            .param("scope", scope)
            .query((rs, rowNum) -> true)
            .single();
    }

    private void lockCurrentSelectionScope(PolicyLifecycleRecord record) {
        String scope = String.join("|",
            record.institutionId(), record.executionPack().name(), record.workloadId(), record.purposeCode()
        );
        jdbcClient.sql("select pg_advisory_xact_lock(hashtextextended(:scope, 0))")
            .param("scope", scope)
            .query((rs, rowNum) -> true)
            .single();
    }

    private Optional<PolicyLifecycleRecord> findOnlyActive(PolicyLifecycleRecord scope) {
        var active = jdbcClient.sql("""
                select artifact_id, artifact_version, artifact_digest, institution_id, policy_layer,
                       execution_pack, workload_id, purpose_code, lifecycle_stage, created_by,
                       revision, created_at, updated_at
                from policy.lifecycle_artifact
                where institution_id = :institutionId and execution_pack = :executionPack
                  and workload_id = :workloadId and purpose_code = :purposeCode
                  and lifecycle_stage = 'ACTIVE'
                order by artifact_id, artifact_version
                limit 2
                """)
            .param("institutionId", scope.institutionId()).param("executionPack", scope.executionPack().name())
            .param("workloadId", scope.workloadId()).param("purposeCode", scope.purposeCode())
            .query((rs, rowNum) -> mapLifecycle(rs)).list();
        if (active.size() > 1) {
            throw new PolicyLifecycleException("POLICY_CURRENT_SELECTION_AMBIGUOUS");
        }
        return active.stream().findFirst();
    }

    private PolicyLifecycleRecord loadSelectedLifecycle(
        PolicyCurrentSelection selection,
        PolicyLifecycleStage expectedStage
    ) {
        PolicyLifecycleRecord record = load(
            selection.institutionId(), Set.of(selection.workloadId()),
            selection.artifactId(), selection.artifactVersion()
        );
        if (record.lifecycleStage() != expectedStage
            || record.policyLayer() != selection.policyLayer()
            || record.executionPack() != selection.executionPack()
            || !Objects.equals(record.purposeCode(), selection.purposeCode())
            || !Objects.equals(record.artifactDigest(), selection.artifactDigest())
            || record.revision() != selection.artifactRevision()) {
            throw new PolicyLifecycleException("POLICY_CURRENT_SELECTION_STALE");
        }
        return record;
    }

    private boolean wasApprovedAndActivated(PolicyLifecycleRecord target) {
        Integer approvals = jdbcClient.sql("""
                select count(*) from policy.lifecycle_transition_event
                where institution_id = :institutionId and artifact_id = :artifactId
                  and artifact_version = :artifactVersion and artifact_digest = :artifactDigest
                  and to_stage = 'APPROVED'
                """)
            .param("institutionId", target.institutionId()).param("artifactId", target.artifactId())
            .param("artifactVersion", target.artifactVersion()).param("artifactDigest", target.artifactDigest())
            .query(Integer.class).single();
        Integer activations = jdbcClient.sql("""
                select count(*) from policy.lifecycle_transition_event
                where institution_id = :institutionId and artifact_id = :artifactId
                  and artifact_version = :artifactVersion and artifact_digest = :artifactDigest
                  and to_stage = 'ACTIVE'
                """)
            .param("institutionId", target.institutionId()).param("artifactId", target.artifactId())
            .param("artifactVersion", target.artifactVersion()).param("artifactDigest", target.artifactDigest())
            .query(Integer.class).single();
        return approvals > 0 && activations > 0;
    }

    private boolean approvalMatchesBaseline(
        PolicyLifecycleRecord candidate,
        PolicyLifecycleRecord baseline
    ) {
        Integer matches = jdbcClient.sql("""
                select count(*) from policy.lifecycle_transition_event
                where institution_id = :institutionId
                  and artifact_id = :candidateId and artifact_version = :candidateVersion
                  and artifact_digest = :candidateDigest and to_stage = 'APPROVED'
                  and approval_gate_version = 'SHADOW_EVIDENCE_V1'
                  and shadow_baseline_artifact_id = :baselineId
                  and shadow_baseline_artifact_version = :baselineVersion
                  and shadow_baseline_artifact_digest = :baselineDigest
                """)
            .param("institutionId", candidate.institutionId())
            .param("candidateId", candidate.artifactId()).param("candidateVersion", candidate.artifactVersion())
            .param("candidateDigest", candidate.artifactDigest()).param("baselineId", baseline.artifactId())
            .param("baselineVersion", baseline.artifactVersion()).param("baselineDigest", baseline.artifactDigest())
            .query(Integer.class).single();
        return matches == 1;
    }

    private void saveCurrentSelection(
        PolicyLifecycleRecord selected,
        long selectionRevision,
        String actorId,
        OffsetDateTime occurredAt
    ) {
        jdbcClient.sql("""
                insert into policy.current_selection (
                    institution_id, policy_layer, execution_pack, workload_id, purpose_code,
                    artifact_id, artifact_version, artifact_digest, artifact_revision,
                    selection_revision, selected_by, selected_at
                ) values (
                    :institutionId, :policyLayer, :executionPack, :workloadId, :purposeCode,
                    :artifactId, :artifactVersion, :artifactDigest, :artifactRevision,
                    :selectionRevision, :actorId, :occurredAt
                ) on conflict (institution_id, execution_pack, workload_id, purpose_code) do update set
                    policy_layer = excluded.policy_layer, artifact_id = excluded.artifact_id,
                    artifact_version = excluded.artifact_version, artifact_digest = excluded.artifact_digest,
                    artifact_revision = excluded.artifact_revision,
                    selection_revision = excluded.selection_revision,
                    selected_by = excluded.selected_by, selected_at = excluded.selected_at
                """)
            .param("institutionId", selected.institutionId()).param("policyLayer", selected.policyLayer().name())
            .param("executionPack", selected.executionPack().name()).param("workloadId", selected.workloadId())
            .param("purposeCode", selected.purposeCode()).param("artifactId", selected.artifactId())
            .param("artifactVersion", selected.artifactVersion()).param("artifactDigest", selected.artifactDigest())
            .param("artifactRevision", selected.revision()).param("selectionRevision", selectionRevision)
            .param("actorId", actorId).param("occurredAt", occurredAt).update();
    }

    private void recordSelectionEvent(
        String eventType,
        PolicyLifecycleRecord previous,
        PolicyLifecycleRecord selected,
        long selectionRevision,
        String actorId,
        PolicyLifecycleTransitionReason reason,
        OffsetDateTime occurredAt
    ) {
        jdbcClient.sql("""
                insert into policy.current_selection_event (
                    institution_id, policy_layer, execution_pack, workload_id, purpose_code, event_type,
                    previous_artifact_id, previous_artifact_version, previous_artifact_digest,
                    selected_artifact_id, selected_artifact_version, selected_artifact_digest,
                    selected_artifact_revision, selection_revision, actor_id, reason_code, occurred_at
                ) values (
                    :institutionId, :policyLayer, :executionPack, :workloadId, :purposeCode, :eventType,
                    :previousId, :previousVersion, :previousDigest,
                    :selectedId, :selectedVersion, :selectedDigest,
                    :artifactRevision, :selectionRevision, :actorId, :reasonCode, :occurredAt
                )
                """)
            .param("institutionId", selected.institutionId()).param("policyLayer", selected.policyLayer().name())
            .param("executionPack", selected.executionPack().name()).param("workloadId", selected.workloadId())
            .param("purposeCode", selected.purposeCode()).param("eventType", eventType)
            .param("previousId", previous == null ? null : previous.artifactId())
            .param("previousVersion", previous == null ? null : previous.artifactVersion())
            .param("previousDigest", previous == null ? null : previous.artifactDigest())
            .param("selectedId", selected.artifactId()).param("selectedVersion", selected.artifactVersion())
            .param("selectedDigest", selected.artifactDigest()).param("artifactRevision", selected.revision())
            .param("selectionRevision", selectionRevision).param("actorId", actorId)
            .param("reasonCode", reason.name()).param("occurredAt", occurredAt).update();
    }

    private PolicyCurrentSelection requireCurrentSelection(PolicyLifecycleRecord record) {
        return findCurrentSelection(
            record.institutionId(), record.executionPack(), record.workloadId(), record.purposeCode()
        ).orElseThrow(() -> new PolicyLifecycleException("POLICY_CURRENT_SELECTION_NOT_FOUND"));
    }

    private PolicyLifecycleRecord mapLifecycle(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new PolicyLifecycleRecord(
            rs.getString("artifact_id"), rs.getString("artifact_version"), rs.getString("artifact_digest"),
            rs.getString("institution_id"), PolicyLayer.valueOf(rs.getString("policy_layer")),
            ExecutionPackType.valueOf(rs.getString("execution_pack")), rs.getString("workload_id"),
            rs.getString("purpose_code"), PolicyLifecycleStage.valueOf(rs.getString("lifecycle_stage")),
            rs.getString("created_by"), rs.getLong("revision"),
            rs.getObject("created_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class)
        );
    }

    private boolean sameCandidate(PolicyLifecycleRecord expected, PolicyLifecycleRecord actual) {
        return sameIdentityAndScope(expected, actual)
            && expected.revision() == actual.revision()
            && actual.lifecycleStage() == PolicyLifecycleStage.REPLAY;
    }

    private boolean sameBaseline(PolicyLifecycleRecord expected, PolicyLifecycleRecord actual) {
        return sameIdentityAndScope(expected, actual)
            && expected.revision() == actual.revision()
            && actual.lifecycleStage() == PolicyLifecycleStage.ACTIVE;
    }

    private boolean sameCurrentApprovalCandidate(PolicyLifecycleRecord expected, PolicyLifecycleRecord actual) {
        return sameIdentityAndScope(expected, actual)
            && expected.revision() == actual.revision()
            && actual.lifecycleStage() == PolicyLifecycleStage.SHADOW;
    }

    private boolean sameApprovalBaseline(
        PolicyApprovalEvidenceBinding binding,
        PolicyLifecycleRecord active
    ) {
        return Objects.equals(binding.baselineArtifactId(), active.artifactId())
            && Objects.equals(binding.baselineArtifactVersion(), active.artifactVersion())
            && Objects.equals(binding.baselineArtifactDigest(), active.artifactDigest())
            && active.lifecycleStage() == PolicyLifecycleStage.ACTIVE;
    }

    private boolean sameIdentityAndScope(PolicyLifecycleRecord expected, PolicyLifecycleRecord actual) {
        return Objects.equals(expected.artifactId(), actual.artifactId())
            && Objects.equals(expected.artifactVersion(), actual.artifactVersion())
            && Objects.equals(expected.artifactDigest(), actual.artifactDigest())
            && Objects.equals(expected.institutionId(), actual.institutionId())
            && expected.policyLayer() == actual.policyLayer()
            && expected.executionPack() == actual.executionPack()
            && Objects.equals(expected.workloadId(), actual.workloadId())
            && Objects.equals(expected.purposeCode(), actual.purposeCode());
    }
}
