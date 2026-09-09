package com.adp.gateway.policy.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;
import java.util.UUID;

import com.adp.gateway.auth.domain.AdpRole;
import com.adp.gateway.auth.domain.AuthPrincipal;
import com.adp.gateway.auth.domain.PrincipalType;
import com.adp.gateway.policy.application.PolicyOperationsReadService;
import com.adp.gateway.policy.application.PolicyLifecycleException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
    "adp.local-fixtures.enabled=true",
    "adp.local-user-auth.enabled=true"
})
@AutoConfigureMockMvc
class PolicyOperationsReadControllerTests {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private PolicyOperationsReadService service;

    @AfterEach
    void removeOperationsFixtures() {
        jdbcClient.sql("delete from policy.shadow_evaluation_evidence where evaluated_by = 'operations-evaluator'")
            .update();
        jdbcClient.sql("delete from policy.lifecycle_transition_event where actor_id = 'operations-maker'")
            .update();
        jdbcClient.sql("delete from policy.lifecycle_artifact where created_by = 'operations-maker'")
            .update();
    }

    @Test
    void searchesAttentionQueueAndLoadsTransitionAndShadowHistory() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String candidateId = "operations-candidate-" + suffix;
        String baselineId = "operations-baseline-" + suffix;
        seedArtifact(candidateId, "institution_local", "customer_summary", "SHADOW", "AI");
        seedArtifact(baselineId, "institution_local", "customer_summary", "ACTIVE", "AI");
        seedTransition(candidateId);
        seedShadow(candidateId, baselineId, suffix);

        mockMvc.perform(get("/api/admin/policy-lifecycle")
                .header("X-ADP-User-Id", "operations-reader")
                .header("X-ADP-User-Roles", "OPERATOR")
                .param("executionPack", "AI")
                .param("attentionRequired", "true")
                .param("query", suffix))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.items[0].artifactId").value(candidateId))
            .andExpect(jsonPath("$.items[0].lifecycleStage").value("SHADOW"));

        mockMvc.perform(get(
                "/api/admin/policy-lifecycle/{artifactId}/versions/1.0.0/history", candidateId
            )
                .header("X-ADP-User-Id", "operations-reader")
                .header("X-ADP-User-Roles", "AUDITOR"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.artifact.artifactId").value(candidateId))
            .andExpect(jsonPath("$.transitions[0].toStage").value("SHADOW"))
            .andExpect(jsonPath("$.shadowEvaluations[0].candidateArtifactId").value(candidateId));
    }

    @Test
    void enforcesInstitutionAndWorkloadScopeInSearchAndHistory() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        seedArtifact("allowed-" + suffix, "institution-a", "workload-a", "DRAFT", "AI");
        seedArtifact("other-workload-" + suffix, "institution-a", "workload-b", "DRAFT", "AI");
        seedArtifact("other-tenant-" + suffix, "institution-b", "workload-a", "DRAFT", "AI");
        AuthPrincipal principal = principal("institution-a", Set.of("workload-a"));

        var page = service.search(principal, null, null, null, suffix, false, 20, 0);

        assertThat(page.items()).extracting(item -> item.artifactId())
            .containsExactly("allowed-" + suffix);
        assertThatThrownBy(() -> service.history(principal, "other-workload-" + suffix, "1.0.0"))
            .isInstanceOf(PolicyLifecycleException.class)
            .extracting(exception -> ((PolicyLifecycleException) exception).reasonCode())
            .isEqualTo("POLICY_LIFECYCLE_ARTIFACT_NOT_FOUND");
        assertThatThrownBy(() -> service.history(principal, "other-tenant-" + suffix, "1.0.0"))
            .isInstanceOf(PolicyLifecycleException.class)
            .extracting(exception -> ((PolicyLifecycleException) exception).reasonCode())
            .isEqualTo("POLICY_LIFECYCLE_ARTIFACT_NOT_FOUND");
    }

    private void seedArtifact(
        String artifactId, String institutionId, String workloadId, String stage, String pack
    ) {
        jdbcClient.sql("""
                insert into policy.lifecycle_artifact (
                    artifact_id, artifact_version, artifact_digest, institution_id, policy_layer,
                    execution_pack, workload_id, purpose_code, lifecycle_stage, created_by,
                    revision, created_at, updated_at
                ) values (
                    :artifactId, '1.0.0', :digest, :institutionId, 'WORKLOAD',
                    :pack, :workloadId, 'CUSTOMER_SUPPORT', :stage, 'operations-maker',
                    4, now(), now()
                )
                """)
            .param("artifactId", artifactId)
            .param("digest", "a".repeat(64))
            .param("institutionId", institutionId)
            .param("pack", pack)
            .param("workloadId", workloadId)
            .param("stage", stage)
            .update();
    }

    private void seedTransition(String artifactId) {
        jdbcClient.sql("""
                insert into policy.lifecycle_transition_event (
                    institution_id, artifact_id, artifact_version, from_stage, to_stage,
                    actor_id, reason_code, artifact_digest, occurred_at, approval_gate_version
                ) values (
                    'institution_local', :artifactId, '1.0.0', 'REPLAY', 'SHADOW',
                    'operations-maker', 'SHADOW_PASSED', :digest, now(), 'NOT_APPLICABLE'
                )
                """)
            .param("artifactId", artifactId)
            .param("digest", "a".repeat(64))
            .update();
    }

    private void seedShadow(String candidateId, String baselineId, String suffix) {
        jdbcClient.sql("""
                insert into policy.shadow_evaluation_evidence (
                    shadow_evaluation_id, institution_id, workload_id, purpose_code,
                    baseline_artifact_id, baseline_artifact_version, baseline_artifact_digest,
                    candidate_artifact_id, candidate_artifact_version, candidate_artifact_digest,
                    candidate_revision, evaluation_case_id, evaluation_case_version, input_digest,
                    baseline_outcome_digest, candidate_outcome_digest, diff_fields, result,
                    evaluated_by, evaluated_at
                ) values (
                    :shadowId, 'institution_local', 'customer_summary', 'CUSTOMER_SUPPORT',
                    :baselineId, '1.0.0', :digest, :candidateId, '1.0.0', :digest,
                    4, 'GOLDEN_ALLOW', '1.0.0', :inputDigest,
                    :outcomeDigest, :outcomeDigest, '[]'::jsonb, 'MATCH',
                    'operations-evaluator', now()
                )
                """)
            .param("shadowId", "shadow-" + suffix)
            .param("baselineId", baselineId)
            .param("candidateId", candidateId)
            .param("digest", "a".repeat(64))
            .param("inputDigest", "b".repeat(64))
            .param("outcomeDigest", "c".repeat(64))
            .update();
    }

    private AuthPrincipal principal(String institutionId, Set<String> workloads) {
        return new AuthPrincipal(
            "operations-reader", PrincipalType.USER, "operations-reader", institutionId,
            false, workloads, Set.of(AdpRole.OPERATOR)
        );
    }
}
