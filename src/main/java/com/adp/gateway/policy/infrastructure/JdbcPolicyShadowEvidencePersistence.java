package com.adp.gateway.policy.infrastructure;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Set;

import com.adp.gateway.policy.application.PolicyLifecycleException;
import com.adp.gateway.policy.application.PolicyShadowEvidencePersistence;
import com.adp.gateway.policy.domain.PolicyShadowEvidence;
import com.adp.gateway.policy.domain.PolicyShadowDiffField;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class JdbcPolicyShadowEvidencePersistence implements PolicyShadowEvidencePersistence {
    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;

    public JdbcPolicyShadowEvidencePersistence(JdbcClient jdbcClient, ObjectMapper objectMapper) {
        this.jdbcClient = jdbcClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public PolicyShadowEvidence save(PolicyShadowEvidence value) {
        try {
            jdbcClient.sql("""
                    insert into policy.shadow_evaluation_evidence (
                        shadow_evaluation_id, institution_id, workload_id, purpose_code,
                        baseline_artifact_id, baseline_artifact_version, baseline_artifact_digest,
                        candidate_artifact_id, candidate_artifact_version, candidate_artifact_digest,
                        candidate_revision, evaluation_case_id, evaluation_case_version, input_digest,
                        baseline_outcome_digest, candidate_outcome_digest, diff_fields, result,
                        evaluated_by, evaluated_at
                    ) values (
                        :id, :institutionId, :workloadId, :purposeCode,
                        :baselineId, :baselineVersion, :baselineDigest,
                        :candidateId, :candidateVersion, :candidateDigest,
                        :candidateRevision, :caseId, :caseVersion, :inputDigest,
                        :baselineOutcomeDigest, :candidateOutcomeDigest, cast(:diffFields as jsonb), :result,
                        :evaluatedBy, :evaluatedAt
                    )
                    """)
                .param("id", value.shadowEvaluationId())
                .param("institutionId", value.institutionId())
                .param("workloadId", value.workloadId())
                .param("purposeCode", value.purposeCode())
                .param("baselineId", value.baselineArtifactId())
                .param("baselineVersion", value.baselineArtifactVersion())
                .param("baselineDigest", value.baselineArtifactDigest())
                .param("candidateId", value.candidateArtifactId())
                .param("candidateVersion", value.candidateArtifactVersion())
                .param("candidateDigest", value.candidateArtifactDigest())
                .param("candidateRevision", value.candidateRevision())
                .param("caseId", value.evaluationCaseId())
                .param("caseVersion", value.evaluationCaseVersion())
                .param("inputDigest", value.inputDigest())
                .param("baselineOutcomeDigest", value.baselineOutcomeDigest())
                .param("candidateOutcomeDigest", value.candidateOutcomeDigest())
                .param("diffFields", json(value.diffFields().stream().map(PolicyShadowDiffField::name).toList()))
                .param("result", value.result())
                .param("evaluatedBy", value.evaluatedBy())
                .param("evaluatedAt", value.evaluatedAt())
                .update();
            return value;
        } catch (DuplicateKeyException exception) {
            throw new PolicyLifecycleException("POLICY_SHADOW_EVIDENCE_CONFLICT");
        }
    }

    @Override
    public PolicyShadowEvidence loadLatestForApproval(
        String institutionId,
        Set<String> allowedWorkloads,
        String candidateArtifactId,
        String candidateArtifactVersion,
        String shadowEvaluationId
    ) {
        if (allowedWorkloads == null || allowedWorkloads.isEmpty()) {
            throw new PolicyLifecycleException("POLICY_SHADOW_APPROVAL_EVIDENCE_NOT_FOUND");
        }
        String workloadPredicate = allowedWorkloads.contains("*") ? "" : "and evidence.workload_id in (:workloads)";
        var query = jdbcClient.sql("""
                select evidence.shadow_evaluation_id, evidence.institution_id,
                       evidence.workload_id, evidence.purpose_code,
                       evidence.baseline_artifact_id, evidence.baseline_artifact_version,
                       evidence.baseline_artifact_digest, evidence.candidate_artifact_id,
                       evidence.candidate_artifact_version, evidence.candidate_artifact_digest,
                       evidence.candidate_revision, evidence.evaluation_case_id,
                       evidence.evaluation_case_version, evidence.input_digest,
                       evidence.baseline_outcome_digest, evidence.candidate_outcome_digest,
                       evidence.diff_fields, evidence.result, evidence.evaluated_by, evidence.evaluated_at
                from policy.shadow_evaluation_evidence evidence
                where evidence.shadow_evaluation_id = :shadowEvaluationId
                  and evidence.institution_id = :institutionId
                  and evidence.candidate_artifact_id = :candidateArtifactId
                  and evidence.candidate_artifact_version = :candidateArtifactVersion
                  and not exists (
                      select 1
                      from policy.shadow_evaluation_evidence newer
                      where newer.institution_id = evidence.institution_id
                        and newer.candidate_artifact_id = evidence.candidate_artifact_id
                        and newer.candidate_artifact_version = evidence.candidate_artifact_version
                        and newer.candidate_revision = evidence.candidate_revision
                        and newer.evaluation_case_id = evidence.evaluation_case_id
                        and newer.evaluation_case_version = evidence.evaluation_case_version
                        and (newer.evaluated_at, newer.shadow_evaluation_id)
                            > (evidence.evaluated_at, evidence.shadow_evaluation_id)
                  )
                """ + workloadPredicate)
            .param("shadowEvaluationId", shadowEvaluationId)
            .param("institutionId", institutionId)
            .param("candidateArtifactId", candidateArtifactId)
            .param("candidateArtifactVersion", candidateArtifactVersion);
        if (!allowedWorkloads.contains("*")) {
            query = query.param("workloads", allowedWorkloads);
        }
        return query.query((rs, rowNum) -> new PolicyShadowEvidence(
                rs.getString("shadow_evaluation_id"), rs.getString("institution_id"),
                rs.getString("workload_id"), rs.getString("purpose_code"),
                rs.getString("baseline_artifact_id"), rs.getString("baseline_artifact_version"),
                rs.getString("baseline_artifact_digest"), rs.getString("candidate_artifact_id"),
                rs.getString("candidate_artifact_version"), rs.getString("candidate_artifact_digest"),
                rs.getLong("candidate_revision"), rs.getString("evaluation_case_id"),
                rs.getString("evaluation_case_version"), rs.getString("input_digest"),
                rs.getString("baseline_outcome_digest"), rs.getString("candidate_outcome_digest"),
                diffFields(rs.getString("diff_fields")),
                rs.getString("result"), rs.getString("evaluated_by"),
                rs.getObject("evaluated_at", OffsetDateTime.class)
            ))
            .optional()
            .orElseThrow(() -> new PolicyLifecycleException("POLICY_SHADOW_APPROVAL_EVIDENCE_NOT_FOUND"));
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Policy shadow evidence could not be serialized", exception);
        }
    }

    private java.util.List<PolicyShadowDiffField> diffFields(String value) {
        try {
            return Arrays.stream(objectMapper.readValue(value, String[].class))
                .map(PolicyShadowDiffField::valueOf)
                .toList();
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Policy shadow evidence could not be parsed", exception);
        }
    }
}
