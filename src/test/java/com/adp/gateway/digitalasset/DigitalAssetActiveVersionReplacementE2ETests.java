package com.adp.gateway.digitalasset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.adp.gateway.auth.domain.AdpRole;
import com.adp.gateway.auth.domain.AuthPrincipal;
import com.adp.gateway.auth.domain.PrincipalType;
import com.adp.gateway.digitalasset.application.DigitalAssetArtifactActivationService;
import com.adp.gateway.digitalasset.application.DigitalAssetRuntimeSnapshotService;
import com.adp.gateway.egress.domain.DestinationBinding;
import com.adp.gateway.egress.domain.DestinationProfile;
import com.adp.gateway.egress.domain.ExecutionPackType;
import com.adp.gateway.policy.domain.AnalysisStatus;
import com.adp.gateway.policy.domain.ArtifactDigest;
import com.adp.gateway.policy.domain.PolicyAction;
import com.adp.gateway.policy.domain.PolicyApplicabilitySpec;
import com.adp.gateway.policy.domain.PolicyEvaluation;
import com.adp.gateway.policy.domain.PolicyLifecycleStage;
import com.adp.gateway.policy.domain.PolicySnapshot;
import com.adp.gateway.policy.domain.RuntimeBinding;
import com.adp.gateway.policy.domain.SourcePolicyEvaluationArtifactRef;
import com.adp.gateway.policy.application.PolicyLifecycleException;
import com.adp.gateway.policy.application.PolicyLifecycleService;
import com.adp.gateway.policy.domain.PolicyLifecycleTransitionReason;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

@SpringBootTest
class DigitalAssetActiveVersionReplacementE2ETests {
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-09-08T00:00:00Z");

    @Autowired
    private JdbcClient jdbcClient;
    @Autowired
    private DigitalAssetArtifactActivationService activationService;
    @Autowired
    private DigitalAssetRuntimeSnapshotService snapshotService;
    @Autowired
    private PolicyLifecycleService lifecycleService;

    @Test
    void replacesActiveVersionWithoutChangingSnapshotOfAlreadyStartedExecution() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String institution = "institution-" + suffix;
        String workload = "workload-" + suffix;
        String purpose = "PURPOSE-" + suffix;
        String artifactId = "artifact-" + suffix;
        seedArtifact(institution, workload, purpose, artifactId, "1.0.0", "a".repeat(64), "ACTIVE");
        seedArtifact(institution, workload, purpose, artifactId, "2.0.0", "b".repeat(64), "APPROVED");
        seedActiveSelection(institution, workload, purpose, artifactId);
        seedExecution("exec-a-" + suffix, institution, workload, purpose);
        seedExecution("exec-b-" + suffix, institution, workload, purpose);

        var first = snapshotService.pinIfRequired(
            "exec-a-" + suffix, institution, workload, purpose, destination(workload, purpose), policy(), NOW
        ).orElseThrow();

        AuthPrincipal checker = principal(institution, workload);
        assertThatThrownBy(() -> lifecycleService.transition(
            checker, artifactId, "2.0.0", PolicyLifecycleStage.ACTIVE,
            PolicyLifecycleTransitionReason.ACTIVATION_APPROVED
        )).isInstanceOf(PolicyLifecycleException.class)
            .extracting(exception -> ((PolicyLifecycleException) exception).reasonCode())
            .isEqualTo("POLICY_LIFECYCLE_TRANSITION_INVALID");

        activationService.activate(checker, artifactId, "2.0.0");

        var second = snapshotService.pinIfRequired(
            "exec-b-" + suffix, institution, workload, purpose, destination(workload, purpose), policy(),
            NOW.plusSeconds(1)
        ).orElseThrow();
        var firstReloaded = snapshotService.find("exec-a-" + suffix).orElseThrow();

        assertThat(first.artifactVersion()).isEqualTo("1.0.0");
        assertThat(second.artifactVersion()).isEqualTo("2.0.0");
        assertThat(firstReloaded).isEqualTo(first);
        assertThat(firstReloaded.snapshotDigest()).isNotEqualTo(second.snapshotDigest());
        assertThat(lifecycle(institution, artifactId, "1.0.0")).isEqualTo("SUPERSEDED");
        assertThat(lifecycle(institution, artifactId, "2.0.0")).isEqualTo("ACTIVE");
        assertThat(activeVersion(institution, workload)).isEqualTo("2.0.0");
    }

    private void seedArtifact(
        String institution, String workload, String purpose, String artifactId,
        String version, String digest, String stage
    ) {
        jdbcClient.sql("""
                insert into policy.lifecycle_artifact (
                    artifact_id, artifact_version, artifact_digest, institution_id, policy_layer,
                    execution_pack, workload_id, purpose_code, lifecycle_stage, created_by,
                    revision, created_at, updated_at
                ) values (
                    :artifactId, :version, :digest, :institution, 'WORKLOAD', 'DIGITAL_ASSET',
                    :workload, :purpose, :stage, 'maker', 5, :now, :now
                )
                """)
            .param("artifactId", artifactId).param("version", version).param("digest", digest)
            .param("institution", institution).param("workload", workload).param("purpose", purpose)
            .param("stage", stage).param("now", NOW).update();
        jdbcClient.sql("""
                insert into policy.digital_asset_artifact_ingestion (
                    institution_id, artifact_id, artifact_version, artifact_digest,
                    manifest_schema_version, manifest_reference, canonical_contract_version,
                    canonical_contract_digest, workload_id, purpose_code, destination_profile_id,
                    runtime_control_version, runtime_control_digest, crosswalk_version, crosswalk_digest,
                    file_count, lifecycle_stage, ingested_by, ingested_at
                ) values (
                    :institution, :artifactId, :version, :digest, 'bundle/v1', 'manifest.json',
                    '1.0.0', :contractDigest, :workload, :purpose, 'destination',
                    :version, :controlDigest, :version, :crosswalkDigest, 5, 'CANDIDATE', 'maker', :now
                )
                """)
            .param("institution", institution).param("artifactId", artifactId).param("version", version)
            .param("digest", digest).param("contractDigest", "sha256:" + "c".repeat(64))
            .param("workload", workload).param("purpose", purpose)
            .param("controlDigest", "sha256:" + (version.startsWith("1") ? "d" : "e").repeat(64))
            .param("crosswalkDigest", "sha256:" + (version.startsWith("1") ? "f" : "1").repeat(64))
            .param("now", NOW).update();
    }

    private void seedActiveSelection(String institution, String workload, String purpose, String artifactId) {
        jdbcClient.sql("""
                insert into policy.digital_asset_active_artifact (
                    institution_id, workload_id, purpose_code, artifact_id, artifact_version,
                    artifact_digest, activated_by, activated_at
                ) values (:institution, :workload, :purpose, :artifactId, '1.0.0', :digest, 'checker', :now)
                """)
            .param("institution", institution).param("workload", workload).param("purpose", purpose)
            .param("artifactId", artifactId).param("digest", "a".repeat(64)).param("now", NOW).update();
    }

    private void seedExecution(String executionId, String institution, String workload, String purpose) {
        jdbcClient.sql("""
                insert into runtime.runtime_execution (
                    execution_id, request_id, trace_id, idempotency_key, workload_id, purpose_code,
                    input_digest, status, institution_id, idempotency_institution_id, request_hash,
                    created_at, updated_at
                ) values (
                    :executionId, :requestId, :traceId, :idempotencyKey, :workload, :purpose,
                    :inputDigest, 'AUTHORIZED', :institution, :institution, :requestHash, :now, :now
                )
                """)
            .param("executionId", executionId).param("requestId", "request-" + executionId)
            .param("traceId", "trace-" + executionId).param("idempotencyKey", "idem-" + executionId)
            .param("workload", workload).param("purpose", purpose).param("institution", institution)
            .param("inputDigest", "2".repeat(64)).param("requestHash", "3".repeat(64))
            .param("now", NOW).update();
    }

    private AuthPrincipal principal(String institution, String workload) {
        return new AuthPrincipal(
            "checker", PrincipalType.USER, "Checker", institution, false,
            Set.of(workload), Set.of(AdpRole.PRIVILEGED_OPERATOR)
        );
    }

    private DestinationProfile destination(String workload, String purpose) {
        return new DestinationProfile(
            "destination", "1.0.0", "destination-digest", "contract", "provider",
            ExecutionPackType.DIGITAL_ASSET, "schema", "tenant", "KR", "NO_RETENTION", false,
            "ACTIVE", NOW.minusDays(1), null, List.of(new DestinationBinding(workload, purpose)), List.of()
        );
    }

    private PolicySnapshot policy() {
        return new PolicySnapshot(
            "policy/1.0.0", "policy-digest", NOW.minusDays(1), PolicyLifecycleStage.ACTIVE,
            new SourcePolicyEvaluationArtifactRef("policy", "1.0.0", new ArtifactDigest("sha256", "digest")),
            new PolicyEvaluation(
                List.of(), List.of(), List.of(), List.of(), PolicyAction.ALLOW, List.of(), List.of(),
                new PolicyApplicabilitySpec(
                    AnalysisStatus.VALIDATED, AnalysisStatus.VALIDATED, "validated", List.of(), List.of(),
                    List.of(), new RuntimeBinding("mapped", "CUSTOMER", "workload", "PURPOSE", "binding")
                )
            )
        );
    }

    private String lifecycle(String institution, String artifactId, String version) {
        return jdbcClient.sql("""
                select lifecycle_stage from policy.lifecycle_artifact
                where institution_id = :institution and artifact_id = :artifactId and artifact_version = :version
                """)
            .param("institution", institution).param("artifactId", artifactId).param("version", version)
            .query(String.class).single();
    }

    private String activeVersion(String institution, String workload) {
        return jdbcClient.sql("""
                select artifact_version from policy.digital_asset_active_artifact
                where institution_id = :institution and workload_id = :workload
                """)
            .param("institution", institution).param("workload", workload).query(String.class).single();
    }
}
