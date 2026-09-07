package com.adp.gateway.policy.infrastructure;

import java.time.OffsetDateTime;
import java.util.Set;

import com.adp.gateway.egress.domain.ExecutionPackType;
import com.adp.gateway.policy.application.PolicyLifecycleException;
import com.adp.gateway.policy.application.PolicyLifecyclePersistence;
import com.adp.gateway.policy.domain.PolicyLayer;
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
                    reason_code, artifact_digest, occurred_at
                ) values (
                    :institutionId, :artifactId, :artifactVersion, :fromStage, :toStage, :actorId,
                    :reason, :artifactDigest, :occurredAt
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
}
