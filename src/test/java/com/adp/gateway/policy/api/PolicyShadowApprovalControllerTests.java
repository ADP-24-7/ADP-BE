package com.adp.gateway.policy.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
    "adp.local-fixtures.enabled=true",
    "adp.local-user-auth.enabled=true"
})
@AutoConfigureMockMvc
class PolicyShadowApprovalControllerTests {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private Environment environment;

    @Test
    void approvesMatchingLatestEvidenceAndBindsTransitionEvidence() throws Exception {
        Scope scope = insertScope("1".repeat(64), "1".repeat(64));
        String shadowId = evaluate(scope.candidateId());
        transitionToShadow(scope.candidateId());

        approve(scope.candidateId(), shadowId)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.lifecycleStage").value("APPROVED"))
            .andExpect(jsonPath("$.revision").value(5));

        Integer bound = jdbcClient.sql("""
                select count(*) from policy.lifecycle_transition_event
                where artifact_id = :candidateId and to_stage = 'APPROVED'
                  and approval_gate_version = 'SHADOW_EVIDENCE_V1'
                  and shadow_evaluation_id = :shadowId
                  and shadow_baseline_artifact_id = :baselineId
                  and shadow_evaluation_case_id = 'GOLDEN_ALLOW'
                  and shadow_result = 'MATCH'
                  and approval_policy_version = 'policy-shadow-approval/1.0.0'
                """)
            .param("candidateId", scope.candidateId())
            .param("shadowId", shadowId)
            .param("baselineId", scope.baselineId())
            .query(Integer.class).single();
        assertThat(bound).isEqualTo(1);
    }

    @Test
    void rejectsApprovalWithoutShadowEvidence() throws Exception {
        Scope scope = insertScope("1".repeat(64), "1".repeat(64));
        transitionToShadow(scope.candidateId());

        approve(scope.candidateId(), "shadow_missing")
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.reasonCode").value("POLICY_SHADOW_APPROVAL_EVIDENCE_NOT_FOUND"));
        assertStage(scope.candidateId(), "SHADOW");
    }

    @Test
    void rejectsGenericTransitionApprovalBypass() throws Exception {
        Scope scope = insertScope("1".repeat(64), "1".repeat(64));
        transitionToShadow(scope.candidateId());

        mockMvc.perform(post(
                "/api/admin/policy-lifecycle/{id}/versions/2.0.0/transitions", scope.candidateId()
            )
                .header("X-ADP-User-Id", "approval-checker")
                .header("X-ADP-User-Roles", "PRIVILEGED_OPERATOR")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetStage\":\"APPROVED\",\"reasonCode\":\"APPROVAL_GRANTED\"}"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.reasonCode").value("POLICY_SHADOW_APPROVAL_REQUIRED"));
        assertStage(scope.candidateId(), "SHADOW");
    }

    @Test
    void rejectsShadowEvidenceFromAnotherPolicyScope() throws Exception {
        Scope source = insertScope("1".repeat(64), "1".repeat(64));
        Scope target = insertScope("2".repeat(64), "2".repeat(64));
        String sourceEvidence = evaluate(source.candidateId());
        transitionToShadow(target.candidateId());

        approve(target.candidateId(), sourceEvidence)
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.reasonCode").value("POLICY_SHADOW_APPROVAL_EVIDENCE_NOT_FOUND"));
        assertStage(target.candidateId(), "SHADOW");
    }

    @Test
    void rejectsEvidenceWhenActiveBaselineChangedAfterShadowEvaluation() throws Exception {
        Scope scope = insertScope("1".repeat(64), "1".repeat(64));
        String shadowId = evaluate(scope.candidateId());
        transitionToShadow(scope.candidateId());
        replaceActive(scope);

        approve(scope.candidateId(), shadowId)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.reasonCode").value("POLICY_SHADOW_APPROVAL_EVIDENCE_STALE"));
        assertStage(scope.candidateId(), "SHADOW");
    }

    @Test
    void rejectsDiffEvidenceByServerOwnedApprovalPolicy() throws Exception {
        Scope scope = insertScope("1".repeat(64), "f".repeat(64));
        String shadowId = evaluate(scope.candidateId());
        transitionToShadow(scope.candidateId());

        approve(scope.candidateId(), shadowId)
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.reasonCode").value("POLICY_SHADOW_DIFF_NOT_APPROVABLE"));
        assertStage(scope.candidateId(), "SHADOW");
    }

    @Test
    void rejectsMatchEvidenceFromNonApprovalCase() throws Exception {
        Scope scope = insertScope("1".repeat(64), "1".repeat(64));
        String shadowId = evaluate(scope.candidateId(), "FAILURE_BLOCK");
        transitionToShadow(scope.candidateId());

        approve(scope.candidateId(), shadowId)
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.reasonCode").value("POLICY_SHADOW_CASE_NOT_APPROVABLE"));
        assertStage(scope.candidateId(), "SHADOW");
    }

    @Test
    void rejectsApprovalByCandidateMaker() throws Exception {
        Scope scope = insertScope("1".repeat(64), "1".repeat(64));
        String shadowId = evaluate(scope.candidateId());
        transitionToShadow(scope.candidateId());

        approve(scope.candidateId(), shadowId, "approval-maker", "PRIVILEGED_OPERATOR")
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.reasonCode").value("POLICY_LIFECYCLE_MAKER_CHECKER_VIOLATION"));
        assertStage(scope.candidateId(), "SHADOW");
    }

    @Test
    void rejectsApprovalWithoutPrivilegedOperatorRole() throws Exception {
        Scope scope = insertScope("1".repeat(64), "1".repeat(64));
        String shadowId = evaluate(scope.candidateId());
        transitionToShadow(scope.candidateId());

        approve(scope.candidateId(), shadowId, "approval-operator", "OPERATOR")
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.reasonCode").value("POLICY_LIFECYCLE_FORBIDDEN"));
        assertStage(scope.candidateId(), "SHADOW");
    }

    @Test
    void concurrentApprovalAllowsExactlyOneWinner() throws Exception {
        Scope scope = insertScope("1".repeat(64), "1".repeat(64));
        String shadowId = evaluate(scope.candidateId());
        transitionToShadow(scope.candidateId());
        String lockScope = String.join(
            "|", "institution_local", "WORKLOAD", "AI", scope.workloadId(), "CUSTOMER_SUPPORT"
        );
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try (Connection lockConnection = connection();
             PreparedStatement lock = lockConnection.prepareStatement(
                 "select pg_advisory_lock(hashtextextended(?, 0))"
             )) {
            lock.setString(1, lockScope);
            lock.execute();
            List<Future<MvcResult>> approvals = List.of(
                executor.submit(() -> concurrentApprove(scope.candidateId(), shadowId, "approval-checker-a", start)),
                executor.submit(() -> concurrentApprove(scope.candidateId(), shadowId, "approval-checker-b", start))
            );
            start.countDown();
            assertThat(awaitAdvisoryWaiters(2)).isTrue();
            unlock(lockConnection, lockScope);

            List<MvcResult> results = List.of(approvals.get(0).get(), approvals.get(1).get());
            assertThat(results).extracting(result -> result.getResponse().getStatus())
                .containsExactlyInAnyOrder(200, 409);
            MvcResult rejected = results.stream()
                .filter(result -> result.getResponse().getStatus() == 409)
                .findFirst().orElseThrow();
            assertThat(objectMapper.readTree(rejected.getResponse().getContentAsString()).get("reasonCode").asText())
                .isEqualTo("POLICY_SHADOW_APPROVAL_EVIDENCE_STALE");
        } finally {
            start.countDown();
            executor.shutdownNow();
        }

        Integer approvals = jdbcClient.sql("""
                select count(*) from policy.lifecycle_transition_event
                where artifact_id = :candidateId and to_stage = 'APPROVED'
                """)
            .param("candidateId", scope.candidateId()).query(Integer.class).single();
        Long revision = jdbcClient.sql("""
                select revision from policy.lifecycle_artifact
                where institution_id = 'institution_local' and artifact_id = :candidateId
                """)
            .param("candidateId", scope.candidateId()).query(Long.class).single();
        assertThat(approvals).isEqualTo(1);
        assertThat(revision).isEqualTo(5L);
    }

    private Scope insertScope(String baselineDigest, String candidateDigest) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        Scope scope = new Scope(
            "approval-baseline-" + suffix,
            "approval-candidate-" + suffix,
            "approval_workload_" + suffix
        );
        insertArtifact(scope.baselineId(), "1.0.0", baselineDigest, scope.workloadId(), "ACTIVE", 6);
        insertArtifact(scope.candidateId(), "2.0.0", candidateDigest, scope.workloadId(), "REPLAY", 3);
        return scope;
    }

    private void insertArtifact(
        String id, String version, String digest, String workload, String stage, long revision
    ) {
        jdbcClient.sql("""
                insert into policy.lifecycle_artifact (
                    artifact_id, artifact_version, artifact_digest, institution_id, policy_layer,
                    execution_pack, workload_id, purpose_code, lifecycle_stage, created_by,
                    revision, created_at, updated_at
                ) values (
                    :id, :version, :digest, 'institution_local', 'WORKLOAD',
                    'AI', :workload, 'CUSTOMER_SUPPORT', :stage, 'approval-maker',
                    :revision, now(), now()
                )
                """)
            .param("id", id).param("version", version).param("digest", digest)
            .param("workload", workload).param("stage", stage).param("revision", revision).update();
    }

    private String evaluate(String candidateId) throws Exception {
        return evaluate(candidateId, "GOLDEN_ALLOW");
    }

    private String evaluate(String candidateId, String evaluationCaseId) throws Exception {
        String response = mockMvc.perform(post(
                "/api/admin/policy-lifecycle/{id}/versions/2.0.0/shadow-evaluations", candidateId
            )
                .header("X-ADP-User-Id", "approval-maker")
                .header("X-ADP-User-Roles", "OPERATOR")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"evaluationCaseId\":\"%s\"}".formatted(evaluationCaseId)))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("shadowEvaluationId").asText();
    }

    private void transitionToShadow(String candidateId) throws Exception {
        mockMvc.perform(post(
                "/api/admin/policy-lifecycle/{id}/versions/2.0.0/transitions", candidateId
            )
                .header("X-ADP-User-Id", "approval-maker")
                .header("X-ADP-User-Roles", "OPERATOR")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetStage\":\"SHADOW\",\"reasonCode\":\"SHADOW_PASSED\"}"))
            .andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.ResultActions approve(String candidateId, String shadowId)
        throws Exception {
        return approve(candidateId, shadowId, "approval-checker", "PRIVILEGED_OPERATOR");
    }

    private org.springframework.test.web.servlet.ResultActions approve(
        String candidateId, String shadowId, String actor, String roles
    ) throws Exception {
        return mockMvc.perform(post(
                "/api/admin/policy-lifecycle/{id}/versions/2.0.0/approvals", candidateId
            )
                .header("X-ADP-User-Id", actor)
                .header("X-ADP-User-Roles", roles)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"shadowEvaluationId\":\"%s\"}".formatted(shadowId)));
    }

    private MvcResult concurrentApprove(
        String candidateId,
        String shadowId,
        String actor,
        CountDownLatch start
    ) throws Exception {
        start.await();
        return approve(candidateId, shadowId, actor, "PRIVILEGED_OPERATOR")
            .andReturn();
    }

    private Connection connection() throws Exception {
        return DriverManager.getConnection(
            environment.getRequiredProperty("spring.datasource.url"),
            environment.getRequiredProperty("spring.datasource.username"),
            environment.getRequiredProperty("spring.datasource.password")
        );
    }

    private boolean awaitAdvisoryWaiters(int expected) throws Exception {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
        try (Connection monitor = connection();
             PreparedStatement statement = monitor.prepareStatement("""
                 select count(*)
                 from pg_stat_activity
                 where datname = current_database() and wait_event = 'advisory'
                 """)) {
            while (System.nanoTime() < deadline) {
                try (ResultSet result = statement.executeQuery()) {
                    result.next();
                    if (result.getInt(1) >= expected) {
                        return true;
                    }
                }
                Thread.sleep(20);
            }
            return false;
        }
    }

    private void unlock(Connection connection, String lockScope) throws Exception {
        try (PreparedStatement unlock = connection.prepareStatement(
            "select pg_advisory_unlock(hashtextextended(?, 0))"
        )) {
            unlock.setString(1, lockScope);
            unlock.execute();
        }
    }

    private void replaceActive(Scope scope) {
        jdbcClient.sql("""
                update policy.lifecycle_artifact
                set lifecycle_stage = 'SUPERSEDED', revision = revision + 1, updated_at = now()
                where institution_id = 'institution_local' and artifact_id = :baselineId
                """)
            .param("baselineId", scope.baselineId()).update();
        insertArtifact(
            scope.baselineId() + "-new", "1.1.0", "2".repeat(64), scope.workloadId(), "ACTIVE", 6
        );
    }

    private void assertStage(String candidateId, String expected) {
        String stage = jdbcClient.sql("""
                select lifecycle_stage from policy.lifecycle_artifact
                where institution_id = 'institution_local' and artifact_id = :candidateId
                """)
            .param("candidateId", candidateId).query(String.class).single();
        assertThat(stage).isEqualTo(expected);
    }

    private record Scope(String baselineId, String candidateId, String workloadId) {
    }
}
