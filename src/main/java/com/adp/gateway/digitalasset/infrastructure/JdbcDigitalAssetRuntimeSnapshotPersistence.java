package com.adp.gateway.digitalasset.infrastructure;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.adp.gateway.digitalasset.application.DigitalAssetRuntimeSnapshotException;
import com.adp.gateway.digitalasset.application.DigitalAssetRuntimeSnapshotPersistence;
import com.adp.gateway.digitalasset.domain.DigitalAssetActiveArtifact;
import com.adp.gateway.digitalasset.domain.DigitalAssetRuntimeSnapshot;
import com.adp.gateway.digitalasset.domain.DigitalAssetPreExecutionGuardResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.adp.gateway.digitalasset.domain.DigitalAssetArtifactControl;
import com.adp.gateway.common.error.ReasonCode;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class JdbcDigitalAssetRuntimeSnapshotPersistence implements DigitalAssetRuntimeSnapshotPersistence {
    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;

    public JdbcDigitalAssetRuntimeSnapshotPersistence(JdbcClient jdbcClient, ObjectMapper objectMapper) {
        this.jdbcClient = jdbcClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public void lockActiveScope(String institutionId, String workloadId) {
        jdbcClient.sql("""
                select 1
                from (select pg_advisory_xact_lock(hashtextextended(:identity, 0))) acquired
                """)
            .param("identity", institutionId + "\u001f" + workloadId)
            .query(Integer.class)
            .single();
    }

    @Override
    public void replaceActive(DigitalAssetActiveArtifact value) {
        jdbcClient.sql("""
                    insert into policy.digital_asset_active_artifact (
                        institution_id, workload_id, purpose_code, artifact_id, artifact_version,
                        artifact_digest, activated_by, activated_at
                    ) values (
                        :institutionId, :workloadId, :purposeCode, :artifactId, :artifactVersion,
                        :artifactDigest, :activatedBy, :activatedAt
                    )
                    on conflict (institution_id, workload_id) do update set
                        purpose_code = excluded.purpose_code,
                        artifact_id = excluded.artifact_id,
                        artifact_version = excluded.artifact_version,
                        artifact_digest = excluded.artifact_digest,
                        activated_by = excluded.activated_by,
                        activated_at = excluded.activated_at
                    """)
                .param("institutionId", value.institutionId())
                .param("workloadId", value.workloadId())
                .param("purposeCode", value.purposeCode())
                .param("artifactId", value.artifactId())
                .param("artifactVersion", value.artifactVersion())
                .param("artifactDigest", value.artifactDigest())
                .param("activatedBy", value.activatedBy())
            .param("activatedAt", value.activatedAt())
            .update();
    }

    @Override
    public Optional<DigitalAssetActiveArtifact> loadActive(
        String institutionId,
        String workloadId
    ) {
        return jdbcClient.sql("""
                select aa.institution_id, aa.workload_id, aa.purpose_code,
                       aa.artifact_id, aa.artifact_version, aa.artifact_digest,
                       ai.destination_profile_id, ai.runtime_control_version,
                       ai.runtime_control_digest, ai.crosswalk_version, ai.crosswalk_digest,
                       aa.activated_by, aa.activated_at
                from policy.digital_asset_active_artifact aa
                join policy.digital_asset_artifact_ingestion ai
                  on ai.institution_id = aa.institution_id
                 and ai.artifact_id = aa.artifact_id
                 and ai.artifact_version = aa.artifact_version
                join policy.lifecycle_artifact la
                  on la.institution_id = aa.institution_id
                 and la.artifact_id = aa.artifact_id
                 and la.artifact_version = aa.artifact_version
                where aa.institution_id = :institutionId
                  and aa.workload_id = :workloadId
                  and la.lifecycle_stage = 'ACTIVE'
                  and aa.artifact_digest = ai.artifact_digest
                  and aa.artifact_digest = la.artifact_digest
                  and aa.workload_id = ai.workload_id
                  and aa.workload_id = la.workload_id
                  and aa.purpose_code = ai.purpose_code
                  and aa.purpose_code = la.purpose_code
                """)
            .param("institutionId", institutionId)
            .param("workloadId", workloadId)
            .query((rs, rowNum) -> new DigitalAssetActiveArtifact(
                rs.getString("institution_id"), rs.getString("workload_id"), rs.getString("purpose_code"),
                rs.getString("artifact_id"), rs.getString("artifact_version"), rs.getString("artifact_digest"),
                rs.getString("destination_profile_id"), rs.getString("runtime_control_version"),
                rs.getString("runtime_control_digest"), rs.getString("crosswalk_version"),
                rs.getString("crosswalk_digest"), rs.getString("activated_by"),
                rs.getObject("activated_at", OffsetDateTime.class)
            )).optional();
    }

    @Override
    public void save(DigitalAssetRuntimeSnapshot value) {
        try {
            jdbcClient.sql("""
                    insert into runtime.digital_asset_runtime_snapshot (
                        snapshot_id, snapshot_digest, execution_id, institution_id, workload_id, purpose_code,
                        artifact_id, artifact_version, artifact_digest, approved_policy_snapshot_id,
                        approved_policy_version, approved_policy_digest, destination_profile_id,
                        destination_profile_version, destination_profile_digest, runtime_control_version,
                        runtime_control_digest, crosswalk_version, crosswalk_digest, selected_at
                    ) values (
                        :snapshotId, :snapshotDigest, :executionId, :institutionId, :workloadId, :purposeCode,
                        :artifactId, :artifactVersion, :artifactDigest, :policySnapshotId,
                        :policyVersion, :policyDigest, :destinationId, :destinationVersion,
                        :destinationDigest, :controlVersion, :controlDigest, :crosswalkVersion,
                        :crosswalkDigest, :selectedAt
                    )
                    """)
                .param("snapshotId", value.snapshotId()).param("snapshotDigest", value.snapshotDigest())
                .param("executionId", value.executionId()).param("institutionId", value.institutionId())
                .param("workloadId", value.workloadId()).param("purposeCode", value.purposeCode())
                .param("artifactId", value.artifactId()).param("artifactVersion", value.artifactVersion())
                .param("artifactDigest", value.artifactDigest())
                .param("policySnapshotId", value.approvedPolicySnapshotId())
                .param("policyVersion", value.approvedPolicyVersion()).param("policyDigest", value.approvedPolicyDigest())
                .param("destinationId", value.destinationProfileId())
                .param("destinationVersion", value.destinationProfileVersion())
                .param("destinationDigest", value.destinationProfileDigest())
                .param("controlVersion", value.runtimeControlVersion()).param("controlDigest", value.runtimeControlDigest())
                .param("crosswalkVersion", value.crosswalkVersion()).param("crosswalkDigest", value.crosswalkDigest())
                .param("selectedAt", value.selectedAt())
                .update();
        } catch (DuplicateKeyException exception) {
            throw new DigitalAssetRuntimeSnapshotException(
                "DIGITAL_ASSET_RUNTIME_SNAPSHOT_CONFLICT", exception
            );
        }
    }

    @Override
    public Optional<DigitalAssetRuntimeSnapshot> findByExecutionId(String executionId) {
        return jdbcClient.sql("""
                select snapshot_id, snapshot_digest, execution_id, institution_id, workload_id, purpose_code,
                       artifact_id, artifact_version, artifact_digest, approved_policy_snapshot_id,
                       approved_policy_version, approved_policy_digest, destination_profile_id,
                       destination_profile_version, destination_profile_digest, runtime_control_version,
                       runtime_control_digest, crosswalk_version, crosswalk_digest, selected_at
                from runtime.digital_asset_runtime_snapshot where execution_id = :executionId
                """)
            .param("executionId", executionId)
            .query((rs, rowNum) -> new DigitalAssetRuntimeSnapshot(
                rs.getString("snapshot_id"), rs.getString("snapshot_digest"), rs.getString("execution_id"),
                rs.getString("institution_id"), rs.getString("workload_id"), rs.getString("purpose_code"),
                rs.getString("artifact_id"), rs.getString("artifact_version"), rs.getString("artifact_digest"),
                rs.getString("approved_policy_snapshot_id"), rs.getString("approved_policy_version"),
                rs.getString("approved_policy_digest"), rs.getString("destination_profile_id"),
                rs.getString("destination_profile_version"), rs.getString("destination_profile_digest"),
                rs.getString("runtime_control_version"), rs.getString("runtime_control_digest"),
                rs.getString("crosswalk_version"), rs.getString("crosswalk_digest"),
                rs.getObject("selected_at", OffsetDateTime.class)
            )).optional();
    }

    @Override
    public void savePreExecutionGuard(DigitalAssetPreExecutionGuardResult value) {
        jdbcClient.sql("""
                insert into runtime.digital_asset_pre_execution_guard (
                    execution_id, snapshot_id, status, control_results, reason_codes,
                    outbound_payload_digest, provider_payload_digest, evaluated_at
                ) values (
                    :executionId, :snapshotId, :status, cast(:controlResults as jsonb),
                    cast(:reasonCodes as jsonb), :outboundDigest, :providerDigest, :evaluatedAt
                )
                """)
            .param("executionId", value.executionId())
            .param("snapshotId", value.snapshotId())
            .param("status", value.status())
            .param("controlResults", toJson(value.controlResults()))
            .param("reasonCodes", toJson(value.reasonCodes()))
            .param("outboundDigest", value.outboundPayloadDigest())
            .param("providerDigest", value.providerPayloadDigest())
            .param("evaluatedAt", value.evaluatedAt())
            .update();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Digital Asset pre-execution evidence could not be serialized", exception);
        }
    }

    @Override
    public Optional<DigitalAssetPreExecutionGuardResult> findPreExecutionGuard(String executionId) {
        return jdbcClient.sql("""
                select execution_id, snapshot_id, status, control_results::text as control_results,
                       reason_codes::text as reason_codes, outbound_payload_digest,
                       provider_payload_digest, evaluated_at
                from runtime.digital_asset_pre_execution_guard
                where execution_id = :executionId
                """)
            .param("executionId", executionId)
            .query((rs, rowNum) -> new DigitalAssetPreExecutionGuardResult(
                rs.getString("execution_id"), rs.getString("snapshot_id"), rs.getString("status"),
                fromJson(rs.getString("control_results"), new TypeReference<Map<DigitalAssetArtifactControl, String>>() {}),
                fromJson(rs.getString("reason_codes"), new TypeReference<List<ReasonCode>>() {}),
                rs.getString("outbound_payload_digest"), rs.getString("provider_payload_digest"),
                rs.getObject("evaluated_at", OffsetDateTime.class)
            )).optional();
    }

    private <T> T fromJson(String value, TypeReference<T> type) {
        try {
            return objectMapper.readValue(value, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Digital Asset pre-execution evidence could not be read", exception);
        }
    }
}
