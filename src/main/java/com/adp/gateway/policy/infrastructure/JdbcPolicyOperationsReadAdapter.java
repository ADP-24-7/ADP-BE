package com.adp.gateway.policy.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.adp.gateway.egress.domain.ExecutionPackType;
import com.adp.gateway.policy.application.PolicyOperationsReadPort;
import com.adp.gateway.policy.domain.PolicyArtifactHistory;
import com.adp.gateway.policy.domain.PolicyArtifactPage;
import com.adp.gateway.policy.domain.PolicyArtifactSummary;
import com.adp.gateway.policy.domain.PolicyLayer;
import com.adp.gateway.policy.domain.PolicyLifecycleRecord;
import com.adp.gateway.policy.domain.PolicyLifecycleStage;
import com.adp.gateway.policy.domain.PolicyLifecycleTransitionEvent;
import com.adp.gateway.policy.domain.PolicyLifecycleTransitionReason;
import com.adp.gateway.policy.domain.PolicyShadowDiffField;
import com.adp.gateway.policy.domain.PolicyShadowEvidence;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class JdbcPolicyOperationsReadAdapter implements PolicyOperationsReadPort {
    private static final Set<PolicyLifecycleStage> ATTENTION_STAGES = Set.of(
        PolicyLifecycleStage.SHADOW, PolicyLifecycleStage.APPROVED, PolicyLifecycleStage.REVIEW
    );
    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;

    public JdbcPolicyOperationsReadAdapter(JdbcClient jdbcClient, ObjectMapper objectMapper) {
        this.jdbcClient = jdbcClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public PolicyArtifactPage search(
        String institutionId,
        Set<String> allowedWorkloads,
        ExecutionPackType executionPack,
        PolicyLifecycleStage lifecycleStage,
        String workloadId,
        String query,
        boolean attentionRequired,
        int limit,
        int offset
    ) {
        if (allowedWorkloads == null || allowedWorkloads.isEmpty()) {
            return new PolicyArtifactPage(List.of(), 0, limit, offset);
        }
        StringBuilder predicates = new StringBuilder(" where artifact.institution_id = :institutionId");
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("institutionId", institutionId);
        if (!allowedWorkloads.contains("*")) {
            predicates.append(" and artifact.workload_id in (:allowedWorkloads)");
            parameters.put("allowedWorkloads", allowedWorkloads);
        }
        if (executionPack != null) {
            predicates.append(" and artifact.execution_pack = :executionPack");
            parameters.put("executionPack", executionPack.name());
        }
        if (lifecycleStage != null) {
            predicates.append(" and artifact.lifecycle_stage = :lifecycleStage");
            parameters.put("lifecycleStage", lifecycleStage.name());
        }
        if (workloadId != null) {
            predicates.append(" and artifact.workload_id = :workloadId");
            parameters.put("workloadId", workloadId);
        }
        if (query != null) {
            predicates.append("""
                 and (
                    lower(artifact.artifact_id) like :query
                    or lower(artifact.artifact_version) like :query
                    or lower(artifact.workload_id) like :query
                    or lower(artifact.purpose_code) like :query
                    or lower(artifact.created_by) like :query
                 )
                """);
            parameters.put("query", "%" + query.toLowerCase(java.util.Locale.ROOT) + "%");
        }
        if (attentionRequired) {
            predicates.append(" and artifact.lifecycle_stage in (:attentionStages)");
            parameters.put("attentionStages", ATTENTION_STAGES.stream().map(Enum::name).toList());
        }

        long total = jdbcClient.sql("select count(*) from policy.lifecycle_artifact artifact" + predicates)
            .params(parameters)
            .query(Long.class)
            .single();
        Map<String, Object> pageParameters = new HashMap<>(parameters);
        pageParameters.put("limit", limit);
        pageParameters.put("offset", offset);
        List<PolicyArtifactSummary> items = jdbcClient.sql("""
                select artifact.artifact_id, artifact.artifact_version, artifact.artifact_digest,
                       artifact.policy_layer, artifact.execution_pack, artifact.workload_id,
                       artifact.purpose_code, artifact.lifecycle_stage, artifact.created_by,
                       artifact.revision, artifact.created_at, artifact.updated_at,
                       exists (
                           select 1 from policy.current_selection selection
                           where selection.institution_id = artifact.institution_id
                             and selection.artifact_id = artifact.artifact_id
                             and selection.artifact_version = artifact.artifact_version
                       ) as current_selection
                from policy.lifecycle_artifact artifact
                """ + predicates + "\n" + """
                order by artifact.updated_at desc, artifact.artifact_id, artifact.artifact_version
                limit :limit offset :offset
                """)
            .params(pageParameters)
            .query((rs, rowNum) -> summary(rs))
            .list();
        return new PolicyArtifactPage(items, total, limit, offset);
    }

    @Override
    public PolicyArtifactHistory history(PolicyLifecycleRecord artifact) {
        Map<String, Object> identity = Map.of(
            "institutionId", artifact.institutionId(),
            "artifactId", artifact.artifactId(),
            "artifactVersion", artifact.artifactVersion()
        );
        boolean currentSelection = Boolean.TRUE.equals(jdbcClient.sql("""
                select exists (
                    select 1 from policy.current_selection
                    where institution_id = :institutionId
                      and artifact_id = :artifactId
                      and artifact_version = :artifactVersion
                )
                """).params(identity).query(Boolean.class).single());
        List<PolicyLifecycleTransitionEvent> transitions = jdbcClient.sql("""
                select transition_id, from_stage, to_stage, actor_id, reason_code,
                       artifact_digest, approval_gate_version, shadow_evaluation_id, occurred_at
                from policy.lifecycle_transition_event
                where institution_id = :institutionId
                  and artifact_id = :artifactId
                  and artifact_version = :artifactVersion
                order by occurred_at desc, transition_id desc
                """)
            .params(identity)
            .query((rs, rowNum) -> transition(rs))
            .list();
        List<PolicyShadowEvidence> shadows = jdbcClient.sql("""
                select shadow_evaluation_id, institution_id, workload_id, purpose_code,
                       baseline_artifact_id, baseline_artifact_version, baseline_artifact_digest,
                       candidate_artifact_id, candidate_artifact_version, candidate_artifact_digest,
                       candidate_revision, evaluation_case_id, evaluation_case_version, input_digest,
                       baseline_outcome_digest, candidate_outcome_digest, diff_fields, result,
                       evaluated_by, evaluated_at
                from policy.shadow_evaluation_evidence
                where institution_id = :institutionId
                  and candidate_artifact_id = :artifactId
                  and candidate_artifact_version = :artifactVersion
                order by evaluated_at desc, shadow_evaluation_id desc
                """)
            .params(identity)
            .query((rs, rowNum) -> shadow(rs))
            .list();
        return new PolicyArtifactHistory(artifact, currentSelection, transitions, shadows);
    }

    private PolicyArtifactSummary summary(ResultSet rs) throws SQLException {
        return new PolicyArtifactSummary(
            rs.getString("artifact_id"), rs.getString("artifact_version"), rs.getString("artifact_digest"),
            PolicyLayer.valueOf(rs.getString("policy_layer")),
            ExecutionPackType.valueOf(rs.getString("execution_pack")), rs.getString("workload_id"),
            rs.getString("purpose_code"), PolicyLifecycleStage.valueOf(rs.getString("lifecycle_stage")),
            rs.getString("created_by"), rs.getLong("revision"),
            rs.getObject("created_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class),
            rs.getBoolean("current_selection")
        );
    }

    private PolicyLifecycleTransitionEvent transition(ResultSet rs) throws SQLException {
        return new PolicyLifecycleTransitionEvent(
            rs.getLong("transition_id"), PolicyLifecycleStage.valueOf(rs.getString("from_stage")),
            PolicyLifecycleStage.valueOf(rs.getString("to_stage")), rs.getString("actor_id"),
            PolicyLifecycleTransitionReason.valueOf(rs.getString("reason_code")),
            rs.getString("artifact_digest"), rs.getString("approval_gate_version"),
            rs.getString("shadow_evaluation_id"), rs.getObject("occurred_at", OffsetDateTime.class)
        );
    }

    private PolicyShadowEvidence shadow(ResultSet rs) throws SQLException {
        return new PolicyShadowEvidence(
            rs.getString("shadow_evaluation_id"), rs.getString("institution_id"),
            rs.getString("workload_id"), rs.getString("purpose_code"),
            rs.getString("baseline_artifact_id"), rs.getString("baseline_artifact_version"),
            rs.getString("baseline_artifact_digest"), rs.getString("candidate_artifact_id"),
            rs.getString("candidate_artifact_version"), rs.getString("candidate_artifact_digest"),
            rs.getLong("candidate_revision"), rs.getString("evaluation_case_id"),
            rs.getString("evaluation_case_version"), rs.getString("input_digest"),
            rs.getString("baseline_outcome_digest"), rs.getString("candidate_outcome_digest"),
            diffFields(rs.getString("diff_fields")), rs.getString("result"), rs.getString("evaluated_by"),
            rs.getObject("evaluated_at", OffsetDateTime.class)
        );
    }

    private List<PolicyShadowDiffField> diffFields(String value) {
        try {
            return Arrays.stream(objectMapper.readValue(value, String[].class))
                .map(PolicyShadowDiffField::valueOf)
                .toList();
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Policy shadow evidence could not be parsed", exception);
        }
    }
}
