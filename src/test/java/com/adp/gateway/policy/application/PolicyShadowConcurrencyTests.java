package com.adp.gateway.policy.application;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

import com.adp.gateway.auth.domain.AdpRole;
import com.adp.gateway.auth.domain.AuthPrincipal;
import com.adp.gateway.auth.domain.PrincipalType;
import com.adp.gateway.context.application.CanonicalValueHasher;
import com.adp.gateway.decision.domain.FinalAction;
import com.adp.gateway.policy.domain.PolicyLifecycleRecord;
import com.adp.gateway.policy.domain.PolicyLifecycleStage;
import com.adp.gateway.policy.domain.PolicyLifecycleTransitionReason;
import com.adp.gateway.policy.domain.PolicyShadowOutcome;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;

@SpringBootTest(properties = "adp.local-fixtures.enabled=false")
@Import(PolicyShadowConcurrencyTests.Configuration.class)
class PolicyShadowConcurrencyTests {
    @Autowired
    private PolicyShadowService shadowService;

    @Autowired
    private PolicyLifecycleService lifecycleService;

    @Autowired
    private BlockingPolicyShadowEvaluator evaluator;

    @Autowired
    private JdbcClient jdbcClient;

    @AfterEach
    void releaseEvaluator() {
        evaluator.release();
    }

    @Test
    void rejectsEvidenceWhenCandidateTransitionsDuringEvaluation() throws Exception {
        TestScope scope = insertScope();
        evaluator.block();
        var executor = Executors.newSingleThreadExecutor();
        try {
            var evaluation = executor.submit(() -> shadowService.evaluate(
                operator(), scope.candidateId(), "2.0.0", "GOLDEN_ALLOW"
            ));
            assertThat(evaluator.awaitStarted()).isTrue();

            lifecycleService.transition(
                operator(), scope.candidateId(), "2.0.0", PolicyLifecycleStage.SHADOW,
                PolicyLifecycleTransitionReason.SHADOW_PASSED
            );
            evaluator.release();

            assertStale(evaluation);
            assertNoEvidence(scope.candidateId());
        } finally {
            evaluator.release();
            executor.shutdownNow();
        }
    }

    @Test
    void rejectsEvidenceWhenActiveBaselineIsReplacedDuringEvaluation() throws Exception {
        TestScope scope = insertScope();
        evaluator.block();
        var executor = Executors.newSingleThreadExecutor();
        try {
            var evaluation = executor.submit(() -> shadowService.evaluate(
                operator(), scope.candidateId(), "2.0.0", "GOLDEN_ALLOW"
            ));
            assertThat(evaluator.awaitStarted()).isTrue();

            replaceActiveBaseline(scope);
            evaluator.release();

            assertStale(evaluation);
            assertNoEvidence(scope.candidateId());
        } finally {
            evaluator.release();
            executor.shutdownNow();
        }
    }

    private void assertStale(java.util.concurrent.Future<?> evaluation) {
        assertThatThrownBy(() -> evaluation.get(5, SECONDS))
            .isInstanceOf(ExecutionException.class)
            .hasCauseInstanceOf(PolicyLifecycleException.class)
            .cause()
            .extracting(exception -> ((PolicyLifecycleException) exception).reasonCode())
            .isEqualTo("POLICY_SHADOW_STALE_EVALUATION");
    }

    private TestScope insertScope() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        TestScope scope = new TestScope(
            "active-concurrency-" + suffix,
            "candidate-concurrency-" + suffix,
            "shadow_concurrency_" + suffix
        );
        insertArtifact(scope.baselineId(), "1.0.0", "1".repeat(64), scope.workloadId(), "ACTIVE", 6);
        insertArtifact(scope.candidateId(), "2.0.0", "f".repeat(64), scope.workloadId(), "REPLAY", 3);
        return scope;
    }

    private void replaceActiveBaseline(TestScope scope) {
        jdbcClient.sql("""
                update policy.lifecycle_artifact
                set lifecycle_stage = 'SUPERSEDED', revision = revision + 1, updated_at = now()
                where institution_id = 'institution_local'
                  and artifact_id = :artifactId and artifact_version = '1.0.0'
                """)
            .param("artifactId", scope.baselineId()).update();
        insertArtifact(
            scope.baselineId() + "-v2", "1.1.0", "2".repeat(64), scope.workloadId(), "ACTIVE", 6
        );
    }

    private void insertArtifact(
        String artifactId,
        String version,
        String digest,
        String workloadId,
        String stage,
        long revision
    ) {
        jdbcClient.sql("""
                insert into policy.lifecycle_artifact (
                    artifact_id, artifact_version, artifact_digest, institution_id, policy_layer,
                    execution_pack, workload_id, purpose_code, lifecycle_stage, created_by,
                    revision, created_at, updated_at
                ) values (
                    :artifactId, :version, :digest, 'institution_local', 'WORKLOAD',
                    'AI', :workloadId, 'CUSTOMER_SUPPORT', :stage, 'concurrency-maker',
                    :revision, now(), now()
                )
                """)
            .param("artifactId", artifactId)
            .param("version", version)
            .param("digest", digest)
            .param("workloadId", workloadId)
            .param("stage", stage)
            .param("revision", revision)
            .update();
    }

    private void assertNoEvidence(String candidateId) {
        Integer count = jdbcClient.sql("""
                select count(*) from policy.shadow_evaluation_evidence
                where candidate_artifact_id = :candidateId
                """)
            .param("candidateId", candidateId)
            .query(Integer.class).single();
        assertThat(count).isZero();
    }

    private AuthPrincipal operator() {
        return new AuthPrincipal(
            "shadow-concurrency-operator", PrincipalType.USER, "Shadow operator", "institution_local",
            false, Set.of("*"), Set.of(AdpRole.OPERATOR)
        );
    }

    private record TestScope(String baselineId, String candidateId, String workloadId) {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Configuration {
        @Bean
        @Primary
        BlockingPolicyShadowEvaluator blockingPolicyShadowEvaluator(CanonicalValueHasher hasher) {
            return new BlockingPolicyShadowEvaluator(hasher);
        }
    }

    static class BlockingPolicyShadowEvaluator implements PolicyShadowEvaluator {
        private final CanonicalValueHasher hasher;
        private volatile CountDownLatch started = new CountDownLatch(0);
        private volatile CountDownLatch proceed = new CountDownLatch(0);

        BlockingPolicyShadowEvaluator(CanonicalValueHasher hasher) {
            this.hasher = hasher;
        }

        void block() {
            started = new CountDownLatch(1);
            proceed = new CountDownLatch(1);
        }

        boolean awaitStarted() throws InterruptedException {
            return started.await(5, SECONDS);
        }

        void release() {
            proceed.countDown();
        }

        @Override
        public PolicyShadowOutcome evaluate(PolicyLifecycleRecord artifact, String evaluationCaseId) {
            started.countDown();
            try {
                if (!proceed.await(5, SECONDS)) {
                    throw new IllegalStateException("Timed out waiting to resume shadow evaluation");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Shadow evaluation interrupted", exception);
            }
            return new PolicyShadowOutcome(
                evaluationCaseId, "1.0.0", hasher.hash("concurrency-case|" + evaluationCaseId),
                FinalAction.ALLOW, List.of("TEST_ALLOW"), List.of(), "NONE", "dest_shadow_test"
            );
        }
    }
}
