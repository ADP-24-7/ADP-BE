package com.adp.gateway.policy.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
    "adp.local-fixtures.enabled=true",
    "adp.local-user-auth.enabled=true"
})
@AutoConfigureMockMvc
class PolicyShadowEvaluationControllerTests {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void evaluatesActiveAndReplayCandidateWithoutCallingConnectorAndStoresRawFreeDiff() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String baselineId = "active-shadow-" + suffix;
        String candidateId = "candidate-shadow-" + suffix;
        String workloadId = "shadow_workload_" + suffix;
        insertActive(baselineId, workloadId, "1".repeat(64));
        createCandidate(candidateId, workloadId, "f".repeat(64));
        transition(candidateId, "VALIDATED", "VALIDATION_PASSED");
        transition(candidateId, "CANDIDATE", "CANDIDATE_PROMOTED");
        transition(candidateId, "REPLAY", "REPLAY_PASSED");
        Integer connectorBefore = jdbcClient.sql("select count(*) from runtime.connector_execution")
            .query(Integer.class).single();

        mockMvc.perform(post(
                "/api/admin/policy-lifecycle/{id}/versions/2.0.0/shadow-evaluations", candidateId
            )
                .header("X-ADP-User-Id", "shadow-operator")
                .header("X-ADP-User-Roles", "OPERATOR")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"evaluationCaseId\":\"GOLDEN_ALLOW\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.baselineArtifactId").value(baselineId))
            .andExpect(jsonPath("$.candidateArtifactId").value(candidateId))
            .andExpect(jsonPath("$.result").value("DIFF"))
            .andExpect(jsonPath("$.diffFields[0]").value("FINAL_ACTION"))
            .andExpect(jsonPath("$.inputDigest").isNotEmpty())
            .andExpect(jsonPath("$.baselineOutcomeDigest").isNotEmpty())
            .andExpect(jsonPath("$.candidateOutcomeDigest").isNotEmpty());

        Integer connectorAfter = jdbcClient.sql("select count(*) from runtime.connector_execution")
            .query(Integer.class).single();
        assertThat(connectorAfter).isEqualTo(connectorBefore);
        Integer evidence = jdbcClient.sql("""
                select count(*) from policy.shadow_evaluation_evidence
                where candidate_artifact_id = :candidateId
                  and result = 'DIFF'
                  and jsonb_exists(diff_fields, 'FINAL_ACTION')
                  and baseline_artifact_digest = :baselineDigest
                  and candidate_artifact_digest = :candidateDigest
                """)
            .param("candidateId", candidateId)
            .param("baselineDigest", "1".repeat(64))
            .param("candidateDigest", "f".repeat(64))
            .query(Integer.class).single();
        assertThat(evidence).isEqualTo(1);
    }

    @Test
    void rejectsCandidateBeforeReplayAndCreatesNoEvidence() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String candidateId = "draft-shadow-" + suffix;
        createCandidate(candidateId, "shadow_workload_" + suffix, "f".repeat(64));

        mockMvc.perform(post(
                "/api/admin/policy-lifecycle/{id}/versions/2.0.0/shadow-evaluations", candidateId
            )
                .header("X-ADP-User-Id", "shadow-operator")
                .header("X-ADP-User-Roles", "OPERATOR")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"evaluationCaseId\":\"GOLDEN_ALLOW\"}"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.reasonCode").value("POLICY_SHADOW_CANDIDATE_NOT_REPLAYED"));

        Integer evidence = jdbcClient.sql("""
                select count(*) from policy.shadow_evaluation_evidence
                where candidate_artifact_id = :candidateId
                """)
            .param("candidateId", candidateId).query(Integer.class).single();
        assertThat(evidence).isZero();
    }

    private void insertActive(String artifactId, String workloadId, String digest) {
        jdbcClient.sql("""
                insert into policy.lifecycle_artifact (
                    artifact_id, artifact_version, artifact_digest, institution_id, policy_layer,
                    execution_pack, workload_id, purpose_code, lifecycle_stage, created_by,
                    revision, created_at, updated_at
                ) values (
                    :artifactId, '1.0.0', :digest, 'institution_local', 'WORKLOAD',
                    'AI', :workloadId, 'CUSTOMER_SUPPORT', 'ACTIVE', 'baseline-checker',
                    6, now(), now()
                )
                """)
            .param("artifactId", artifactId).param("workloadId", workloadId).param("digest", digest).update();
    }

    private void createCandidate(String artifactId, String workloadId, String digest) throws Exception {
        mockMvc.perform(post("/api/admin/policy-lifecycle")
                .header("X-ADP-User-Id", "shadow-maker")
                .header("X-ADP-User-Roles", "OPERATOR")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"artifactId":"%s","artifactVersion":"2.0.0","artifactDigest":"%s",
                     "policyLayer":"WORKLOAD","executionPack":"AI",
                     "workloadId":"%s","purposeCode":"CUSTOMER_SUPPORT"}
                    """.formatted(artifactId, digest, workloadId)))
            .andExpect(status().isCreated());
    }

    private void transition(String artifactId, String stage, String reason) throws Exception {
        mockMvc.perform(post("/api/admin/policy-lifecycle/{id}/versions/2.0.0/transitions", artifactId)
                .header("X-ADP-User-Id", "shadow-maker")
                .header("X-ADP-User-Roles", "OPERATOR")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"targetStage":"%s","reasonCode":"%s"}
                    """.formatted(stage, reason)))
            .andExpect(status().isOk());
    }
}
