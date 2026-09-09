package com.adp.gateway.policy.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import com.adp.gateway.auth.domain.AdpRole;
import com.adp.gateway.auth.domain.AuthPrincipal;
import com.adp.gateway.auth.domain.PrincipalType;
import com.adp.gateway.egress.domain.ExecutionPackType;
import com.adp.gateway.policy.domain.PolicyLayer;
import com.adp.gateway.policy.domain.PolicySelectionContext;
import com.adp.gateway.policy.domain.PolicySnapshotPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

@SpringBootTest(properties = {
    "adp.local-fixtures.enabled=true",
    "adp.mock-runtime.enabled=true"
})
class PolicyCurrentSelectionE2ETests {
    @Autowired
    private PolicyLifecycleService service;

    @Autowired
    private PolicySnapshotPort snapshotPort;

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void activatesAndRollsBackWithoutRewritingAnAlreadyResolvedSnapshot() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String workload = "selection-" + suffix;
        String previousId = "policy-previous-" + suffix;
        String candidateId = "policy-candidate-" + suffix;
        String staleCandidateId = "policy-stale-" + suffix;
        seed(previousId, "1.0.0", "1".repeat(64), workload, "ACTIVE", "maker-old", 6);
        seedHistory(previousId, "1.0.0", "1".repeat(64));
        seed(candidateId, "2.0.0", "2".repeat(64), workload, "APPROVED", "maker-new", 5);
        seedBoundApproval(
            candidateId, "2.0.0", "2".repeat(64), workload,
            previousId, "1.0.0", "1".repeat(64)
        );
        seed(staleCandidateId, "3.0.0", "3".repeat(64), workload, "APPROVED", "maker-stale", 5);
        seedBoundApproval(
            staleCandidateId, "3.0.0", "3".repeat(64), workload,
            previousId, "1.0.0", "1".repeat(64)
        );

        var principal = privileged("checker-" + suffix, workload);
        var activated = service.activate(principal, candidateId, "2.0.0", 5, 0);
        assertThat(activated.artifactId()).isEqualTo(candidateId);
        assertThat(activated.selectionRevision()).isEqualTo(1);

        var context = new PolicySelectionContext(
            workload, "CUSTOMER_SUPPORT", "provider", List.of(), List.of(),
            "institution_local", ExecutionPackType.AI
        );
        var pinnedBeforeRollback = snapshotPort.load(context);
        assertThat(pinnedBeforeRollback.sourcePolicyEvaluationArtifactRef().artifactId()).isEqualTo(candidateId);
        assertThat(pinnedBeforeRollback.currentSelectionRef().selectionRevision()).isEqualTo(1);

        assertThatThrownBy(() -> service.activate(principal, staleCandidateId, "3.0.0", 5, 1))
            .isInstanceOf(PolicyLifecycleException.class)
            .extracting(error -> ((PolicyLifecycleException) error).reasonCode())
            .isEqualTo("POLICY_CURRENT_SELECTION_APPROVAL_STALE");

        assertThatThrownBy(() -> service.rollback(
            principal, previousId, "1.0.0", 7, 0
        )).isInstanceOf(PolicyLifecycleException.class)
            .extracting(error -> ((PolicyLifecycleException) error).reasonCode())
            .isEqualTo("POLICY_LIFECYCLE_CONCURRENT_MODIFICATION");
        assertThatThrownBy(() -> service.rollback(
            privileged("cross-scope-checker", "another-workload"), previousId, "1.0.0", 7, 1
        )).isInstanceOf(PolicyLifecycleException.class)
            .extracting(error -> ((PolicyLifecycleException) error).reasonCode())
            .isEqualTo("POLICY_LIFECYCLE_ARTIFACT_NOT_FOUND");

        var rolledBack = service.rollback(principal, previousId, "1.0.0", 7, 1);
        var selectedAfterRollback = snapshotPort.load(context);
        assertThat(rolledBack.artifactId()).isEqualTo(previousId);
        assertThat(rolledBack.selectionRevision()).isEqualTo(2);
        assertThat(selectedAfterRollback.sourcePolicyEvaluationArtifactRef().artifactId()).isEqualTo(previousId);
        assertThat(selectedAfterRollback.currentSelectionRef().selectionRevision()).isEqualTo(2);
        assertThat(pinnedBeforeRollback.sourcePolicyEvaluationArtifactRef().artifactId()).isEqualTo(candidateId);
        assertThat(stage(candidateId, "2.0.0")).isEqualTo("ROLLED_BACK");
        assertThat(stage(previousId, "1.0.0")).isEqualTo("ACTIVE");
        assertThat(selectionEvents(workload)).isEqualTo(2);
        assertThat(selectionReasons(workload)).containsExactly("ACTIVATION_APPROVED", "ROLLBACK_APPROVED");
    }

    @Test
    void fencesConcurrentActivationsWithTheExpectedSelectionRevision() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String workload = "concurrent-" + suffix;
        String baselineId = "policy-baseline-" + suffix;
        String firstId = "policy-first-" + suffix;
        String secondId = "policy-second-" + suffix;
        seed(baselineId, "1.0.0", "1".repeat(64), workload, "ACTIVE", "maker-baseline", 6);
        seed(firstId, "2.0.0", "2".repeat(64), workload, "APPROVED", "maker-first", 5);
        seed(secondId, "3.0.0", "3".repeat(64), workload, "APPROVED", "maker-second", 5);
        seedBoundApproval(firstId, "2.0.0", "2".repeat(64), workload,
            baselineId, "1.0.0", "1".repeat(64));
        seedBoundApproval(secondId, "3.0.0", "3".repeat(64), workload,
            baselineId, "1.0.0", "1".repeat(64));
        var principal = privileged("checker-" + suffix, workload);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        Callable<String> first = activation(ready, start, principal, firstId, "2.0.0");
        Callable<String> second = activation(ready, start, principal, secondId, "3.0.0");

        try (var executor = Executors.newFixedThreadPool(2)) {
            var firstResult = executor.submit(first);
            var secondResult = executor.submit(second);
            ready.await();
            start.countDown();
            var outcomes = List.of(firstResult.get(), secondResult.get());
            assertThat(outcomes).containsExactlyInAnyOrder("ACTIVE", "POLICY_LIFECYCLE_CONCURRENT_MODIFICATION");
        }

        Integer activeCount = jdbcClient.sql("""
                select count(*) from policy.lifecycle_artifact
                where institution_id = 'institution_local' and execution_pack = 'AI'
                  and workload_id = :workload and purpose_code = 'CUSTOMER_SUPPORT'
                  and lifecycle_stage = 'ACTIVE'
                """).param("workload", workload).query(Integer.class).single();
        assertThat(activeCount).isEqualTo(1);
        assertThat(service.loadCurrentSelection(
            principal, ExecutionPackType.AI, workload, "CUSTOMER_SUPPORT"
        ).selectionRevision()).isEqualTo(1);
    }

    @Test
    void rejectsUnapprovedActivationAndStaleRollbackTarget() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String workload = "rejected-" + suffix;
        String targetId = "policy-target-" + suffix;
        seed(targetId, "1.0.0", "4".repeat(64), workload, "SHADOW", "maker", 4);
        var principal = privileged("checker-" + suffix, workload);

        assertThatThrownBy(() -> service.activate(principal, targetId, "1.0.0", 4, 0))
            .isInstanceOf(PolicyLifecycleException.class)
            .extracting(error -> ((PolicyLifecycleException) error).reasonCode())
            .isEqualTo("POLICY_LIFECYCLE_TRANSITION_INVALID");
    }

    private Callable<String> activation(
        CountDownLatch ready,
        CountDownLatch start,
        AuthPrincipal principal,
        String artifactId,
        String artifactVersion
    ) {
        return () -> {
            ready.countDown();
            start.await();
            try {
                service.activate(principal, artifactId, artifactVersion, 5, 0);
                return "ACTIVE";
            } catch (PolicyLifecycleException exception) {
                return exception.reasonCode();
            }
        };
    }

    private void seed(
        String artifactId,
        String version,
        String digest,
        String workload,
        String stage,
        String maker,
        long revision
    ) {
        jdbcClient.sql("""
                insert into policy.lifecycle_artifact (
                    artifact_id, artifact_version, artifact_digest, institution_id, policy_layer,
                    execution_pack, workload_id, purpose_code, lifecycle_stage, created_by,
                    revision, created_at, updated_at
                ) values (
                    :artifactId, :version, :digest, 'institution_local', 'WORKLOAD',
                    'AI', :workload, 'CUSTOMER_SUPPORT', :stage, :maker,
                    :revision, now(), now()
                )
                """)
            .param("artifactId", artifactId).param("version", version).param("digest", digest)
            .param("workload", workload).param("stage", stage).param("maker", maker)
            .param("revision", revision).update();
    }

    private void seedHistory(String artifactId, String version, String digest) {
        seedEvent(artifactId, version, digest, "SHADOW", "APPROVED", "APPROVAL_GRANTED", "LEGACY_UNBOUND");
        seedEvent(artifactId, version, digest, "APPROVED", "ACTIVE", "ACTIVATION_APPROVED", "NOT_APPLICABLE");
    }

    private void seedBoundApproval(
        String candidateId,
        String candidateVersion,
        String candidateDigest,
        String workload,
        String baselineId,
        String baselineVersion,
        String baselineDigest
    ) {
        String shadowId = "shadow_" + UUID.randomUUID();
        jdbcClient.sql("""
                insert into policy.shadow_evaluation_evidence (
                    shadow_evaluation_id, institution_id, workload_id, purpose_code,
                    baseline_artifact_id, baseline_artifact_version, baseline_artifact_digest,
                    candidate_artifact_id, candidate_artifact_version, candidate_artifact_digest,
                    candidate_revision, evaluation_case_id, evaluation_case_version, input_digest,
                    baseline_outcome_digest, candidate_outcome_digest, diff_fields, result,
                    evaluated_by, evaluated_at
                ) values (
                    :shadowId, 'institution_local', :workload, 'CUSTOMER_SUPPORT',
                    :baselineId, :baselineVersion, :baselineDigest,
                    :candidateId, :candidateVersion, :candidateDigest,
                    3, 'GOLDEN_ALLOW', '1.0.0', repeat('d', 64),
                    repeat('e', 64), repeat('e', 64), '[]'::jsonb, 'MATCH',
                    'shadow-evaluator', now()
                )
                """)
            .param("shadowId", shadowId).param("workload", workload)
            .param("baselineId", baselineId).param("baselineVersion", baselineVersion)
            .param("baselineDigest", baselineDigest).param("candidateId", candidateId)
            .param("candidateVersion", candidateVersion).param("candidateDigest", candidateDigest).update();
        jdbcClient.sql("""
                insert into policy.lifecycle_transition_event (
                    institution_id, artifact_id, artifact_version, from_stage, to_stage,
                    actor_id, reason_code, artifact_digest, occurred_at, approval_gate_version,
                    shadow_evaluation_id, shadow_candidate_revision,
                    shadow_baseline_artifact_id, shadow_baseline_artifact_version,
                    shadow_baseline_artifact_digest, shadow_evaluation_case_id,
                    shadow_evaluation_case_version, shadow_result, approval_policy_version
                ) values (
                    'institution_local', :candidateId, :candidateVersion, 'SHADOW', 'APPROVED',
                    'approval-checker', 'APPROVAL_GRANTED', :candidateDigest, now(), 'SHADOW_EVIDENCE_V1',
                    :shadowId, 3, :baselineId, :baselineVersion, :baselineDigest,
                    'GOLDEN_ALLOW', '1.0.0', 'MATCH', 'policy-shadow-approval/1.0.0'
                )
                """)
            .param("candidateId", candidateId).param("candidateVersion", candidateVersion)
            .param("candidateDigest", candidateDigest).param("shadowId", shadowId)
            .param("baselineId", baselineId).param("baselineVersion", baselineVersion)
            .param("baselineDigest", baselineDigest).update();
    }

    private void seedEvent(
        String artifactId,
        String version,
        String digest,
        String from,
        String to,
        String reason,
        String gate
    ) {
        jdbcClient.sql("""
                insert into policy.lifecycle_transition_event (
                    institution_id, artifact_id, artifact_version, from_stage, to_stage,
                    actor_id, reason_code, artifact_digest, occurred_at, approval_gate_version
                ) values (
                    'institution_local', :artifactId, :version, :fromStage, :toStage,
                    'historical-checker', :reason, :digest, now(), :gate
                )
                """)
            .param("artifactId", artifactId).param("version", version).param("fromStage", from)
            .param("toStage", to).param("reason", reason).param("digest", digest).param("gate", gate).update();
    }

    private String stage(String artifactId, String version) {
        return jdbcClient.sql("""
                select lifecycle_stage from policy.lifecycle_artifact
                where institution_id = 'institution_local' and artifact_id = :artifactId
                  and artifact_version = :version
                """).param("artifactId", artifactId).param("version", version).query(String.class).single();
    }

    private int selectionEvents(String workload) {
        return jdbcClient.sql("""
                select count(*) from policy.current_selection_event
                where institution_id = 'institution_local' and workload_id = :workload
                """).param("workload", workload).query(Integer.class).single();
    }

    private List<String> selectionReasons(String workload) {
        return jdbcClient.sql("""
                select reason_code from policy.current_selection_event
                where institution_id = 'institution_local' and workload_id = :workload
                order by selection_revision
                """).param("workload", workload).query(String.class).list();
    }

    private AuthPrincipal privileged(String id, String workload) {
        return new AuthPrincipal(
            id, PrincipalType.USER, id, "institution_local", false, Set.of(workload),
            Set.of(AdpRole.PRIVILEGED_OPERATOR)
        );
    }
}
