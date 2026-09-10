package com.adp.gateway.digitalasset.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

import com.adp.gateway.digitalasset.application.DigitalAssetArtifactCurrentStateReadPort;
import com.adp.gateway.digitalasset.application.DigitalAssetArtifactIngestionException;
import com.adp.gateway.policy.domain.PolicyLifecycleStage;
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
class DigitalAssetArtifactCurrentStateControllerTests {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private DigitalAssetArtifactCurrentStateReadPort readPort;

    @Test
    void operatorListsArtifactsWithoutOptionalSearchFilters() throws Exception {
        mockMvc.perform(get("/api/admin/digital-assets/artifacts")
                .header("X-ADP-User-Id", "operator-local")
                .header("X-ADP-User-Roles", "OPERATOR")
                .param("page", "0")
                .param("size", "10")
                .param("currentOnly", "false"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items").isArray());
    }

    @Test
    void operatorDiscoversCurrentArtifactWithoutProvidingItsIdentity() throws Exception {
        mockMvc.perform(get("/api/admin/digital-assets/artifacts")
                .header("X-ADP-User-Id", "operator-local")
                .header("X-ADP-User-Roles", "OPERATOR")
                .param("workloadId", "tokenized_asset_purchase")
                .param("currentOnly", "true"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.items[0].artifactId")
                .value("DA-DIGITAL-ASSET-RUNTIME-LOCAL-ACTIVE-001"))
            .andExpect(jsonPath("$.items[0].lifecycleStage").value("ACTIVE"))
            .andExpect(jsonPath("$.items[0].currentSelectionStatus").value("CURRENT"));
    }

    @Test
    void detailConnectsCurrentSelectionToLatestRuntimeAndPolicyEvidence() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String artifactId = "da-current-" + suffix;
        String workloadId = "da-workload-" + suffix;
        String executionId = "exec-da-current-" + suffix;
        seedCurrentArtifact(artifactId, workloadId, executionId);

        mockMvc.perform(get(
                "/api/admin/digital-assets/artifacts/{artifactId}/versions/1.0.0/current-state",
                artifactId
            )
                .header("X-ADP-User-Id", "auditor-local")
                .header("X-ADP-User-Roles", "AUDITOR"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.artifact.currentSelectionStatus").value("CURRENT"))
            .andExpect(jsonPath("$.artifact.runtimeExecutionCount").value(1))
            .andExpect(jsonPath("$.activeSelection.artifactId").value(artifactId))
            .andExpect(jsonPath("$.latestRuntimeEvidence.executionId").value(executionId))
            .andExpect(jsonPath("$.latestRuntimeEvidence.runtimeStatus").value("COMPLETED"))
            .andExpect(jsonPath("$.latestRuntimeEvidence.approvedPolicySnapshotId")
                .value("policy-da-current:1.0.0"))
            .andExpect(jsonPath("$.latestRuntimeEvidence.tracePath")
                .value("/v1/runtime/executions/" + executionId + "/trace"))
            .andExpect(jsonPath("$.latestRuntimeEvidence.evidencePath")
                .value("/api/admin/audit/executions/" + executionId + "/evidence"))
            .andExpect(jsonPath("$.policyHistoryPath")
                .value("/api/admin/policy-lifecycle/" + artifactId + "/versions/1.0.0/history"));
    }

    @Test
    void workloadScopeRestrictsListCountAndDetailAndEmptyScopeFailsClosed() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String artifactId = "da-scoped-" + suffix;
        String workloadId = "da-scoped-workload-" + suffix;
        seedCurrentArtifact(artifactId, workloadId, "exec-da-scoped-" + suffix);

        var visible = readPort.search(
            "institution_local", Set.of(workloadId), null, workloadId,
            artifactId, false, 0, 20
        );
        assertThat(visible.items()).singleElement().satisfies(item ->
            assertThat(item.artifactId()).isEqualTo(artifactId)
        );
        assertThat(visible.totalElements()).isEqualTo(1);

        var hidden = readPort.search(
            "institution_local", Set.of("different-workload"), null, null,
            artifactId, false, 0, 20
        );
        assertThat(hidden.items()).isEmpty();
        assertThat(hidden.totalElements()).isZero();
        assertThatThrownBy(() -> readPort.load(
            "institution_local", Set.of("different-workload"), artifactId, "1.0.0"
        )).isInstanceOf(DigitalAssetArtifactIngestionException.class);

        var empty = readPort.search(
            "institution_local", Set.of(), null, null, artifactId, false, 0, 20
        );
        assertThat(empty.items()).isEmpty();
        assertThat(empty.totalElements()).isZero();
        assertThatThrownBy(() -> readPort.load(
            "institution_local", Set.of(), artifactId, "1.0.0"
        )).isInstanceOf(DigitalAssetArtifactIngestionException.class);
    }

    @Test
    void activeLifecycleWithMismatchedCurrentSelectionIsReportedAsInconsistent() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String artifactId = "da-inconsistent-" + suffix;
        String workloadId = "da-inconsistent-workload-" + suffix;
        seedCurrentArtifact(artifactId, workloadId, "exec-da-inconsistent-" + suffix);

        jdbcClient.sql("""
                update policy.digital_asset_active_artifact
                set artifact_digest = :mismatchedDigest
                where institution_id = 'institution_local'
                  and workload_id = :workloadId
                  and purpose_code = 'DIGITAL_ASSET_PURCHASE'
                """)
            .param("mismatchedDigest", "9".repeat(64))
            .param("workloadId", workloadId)
            .update();

        var detail = readPort.load(
            "institution_local", Set.of(workloadId), artifactId, "1.0.0"
        );

        assertThat(detail.artifact().lifecycleStage()).isEqualTo(PolicyLifecycleStage.ACTIVE);
        assertThat(detail.artifact().currentSelectionStatus())
            .isEqualTo(com.adp.gateway.digitalasset.domain.DigitalAssetCurrentSelectionStatus.INCONSISTENT);
    }

    private void seedCurrentArtifact(String artifactId, String workloadId, String executionId) {
        OffsetDateTime now = OffsetDateTime.now();
        String artifactDigest = "a".repeat(64);
        String snapshotDigest = "sha256:"
            + UUID.randomUUID().toString().replace("-", "")
            + UUID.randomUUID().toString().replace("-", "");
        jdbcClient.sql("""
                insert into policy.lifecycle_artifact (
                    artifact_id, artifact_version, artifact_digest, institution_id, policy_layer,
                    execution_pack, workload_id, purpose_code, lifecycle_stage, created_by,
                    revision, created_at, updated_at
                ) values (
                    :artifactId, '1.0.0', :artifactDigest, 'institution_local', 'WORKLOAD',
                    'DIGITAL_ASSET', :workloadId, 'DIGITAL_ASSET_PURCHASE', 'ACTIVE', 'maker',
                    6, :now, :now
                )
                """)
            .param("artifactId", artifactId)
            .param("artifactDigest", artifactDigest)
            .param("workloadId", workloadId)
            .param("now", now)
            .update();
        jdbcClient.sql("""
                insert into policy.digital_asset_artifact_ingestion (
                    institution_id, artifact_id, artifact_version, artifact_digest,
                    manifest_schema_version, manifest_reference, canonical_contract_version,
                    canonical_contract_digest, workload_id, purpose_code, destination_profile_id,
                    runtime_control_version, runtime_control_digest, crosswalk_version, crosswalk_digest,
                    file_count, lifecycle_stage, ingested_by, ingested_at
                ) values (
                    'institution_local', :artifactId, '1.0.0', :artifactDigest,
                    'adp-digital-asset-artifact-bundle/v1', 'validated/manifest.json', '1.0.0',
                    :contractDigest, :workloadId, 'DIGITAL_ASSET_PURCHASE', 'destination-da-current',
                    '1.0.0', :runtimeDigest, '1.0.0', :crosswalkDigest,
                    5, 'CANDIDATE', 'maker', :now
                )
                """)
            .param("artifactId", artifactId)
            .param("artifactDigest", artifactDigest)
            .param("contractDigest", "sha256:" + "b".repeat(64))
            .param("workloadId", workloadId)
            .param("runtimeDigest", "sha256:" + "c".repeat(64))
            .param("crosswalkDigest", "sha256:" + "d".repeat(64))
            .param("now", now)
            .update();
        jdbcClient.sql("""
                insert into policy.digital_asset_active_artifact (
                    institution_id, workload_id, purpose_code, artifact_id, artifact_version,
                    artifact_digest, activated_by, activated_at
                ) values (
                    'institution_local', :workloadId, 'DIGITAL_ASSET_PURCHASE', :artifactId,
                    '1.0.0', :artifactDigest, 'checker', :now
                )
                """)
            .param("workloadId", workloadId)
            .param("artifactId", artifactId)
            .param("artifactDigest", artifactDigest)
            .param("now", now)
            .update();
        jdbcClient.sql("""
                insert into runtime.runtime_execution (
                    execution_id, request_id, trace_id, idempotency_key, workload_id, purpose_code,
                    input_digest, status, final_action, execution_pack, institution_id,
                    idempotency_institution_id, request_hash, created_at, updated_at
                ) values (
                    :executionId, :requestId, :traceId, :idempotencyKey, :workloadId,
                    'DIGITAL_ASSET_PURCHASE', :inputDigest, 'COMPLETED', 'ALLOW', 'DIGITAL_ASSET',
                    'institution_local', 'institution_local', :requestHash, :now, :now
                )
                """)
            .param("executionId", executionId)
            .param("requestId", "request-" + executionId)
            .param("traceId", "trace-" + executionId)
            .param("idempotencyKey", "idempotency-" + executionId)
            .param("workloadId", workloadId)
            .param("inputDigest", "e".repeat(64))
            .param("requestHash", "f".repeat(64))
            .param("now", now)
            .update();
        jdbcClient.sql("""
                insert into runtime.digital_asset_runtime_snapshot (
                    snapshot_id, snapshot_digest, execution_id, institution_id, workload_id,
                    purpose_code, artifact_id, artifact_version, artifact_digest,
                    approved_policy_snapshot_id, approved_policy_version, approved_policy_digest,
                    destination_profile_id, destination_profile_version, destination_profile_digest,
                    runtime_control_version, runtime_control_digest, crosswalk_version,
                    crosswalk_digest, selected_at
                ) values (
                    :snapshotId, :snapshotDigest, :executionId, 'institution_local', :workloadId,
                    'DIGITAL_ASSET_PURCHASE', :artifactId, '1.0.0', :artifactDigest,
                    'policy-da-current:1.0.0', 'policy/1.0.0', :policyDigest,
                    'destination-da-current', '1.0.0', :destinationDigest,
                    '1.0.0', :runtimeDigest, '1.0.0', :crosswalkDigest, :now
                )
                """)
            .param("snapshotId", "snapshot-" + executionId)
            .param("snapshotDigest", snapshotDigest)
            .param("executionId", executionId)
            .param("workloadId", workloadId)
            .param("artifactId", artifactId)
            .param("artifactDigest", artifactDigest)
            .param("policyDigest", "policy-digest-" + artifactId)
            .param("destinationDigest", "destination-digest-" + artifactId)
            .param("runtimeDigest", "sha256:" + "c".repeat(64))
            .param("crosswalkDigest", "sha256:" + "d".repeat(64))
            .param("now", now)
            .update();
    }
}
