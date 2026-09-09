package com.adp.gateway.policy.infrastructure;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Set;

import com.adp.gateway.egress.domain.ExecutionPackType;
import com.adp.gateway.policy.application.PolicyLifecycleException;
import com.adp.gateway.policy.application.PolicyLifecyclePersistence;
import com.adp.gateway.policy.domain.PolicyLayer;
import com.adp.gateway.policy.domain.PolicyApprovalEvidenceBinding;
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
