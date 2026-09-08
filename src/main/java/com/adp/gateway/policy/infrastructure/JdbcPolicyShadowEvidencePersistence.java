package com.adp.gateway.policy.infrastructure;

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

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Policy shadow evidence could not be serialized", exception);
        }
    }
}
