package com.adp.gateway.digitalasset.infrastructure;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Set;

import com.adp.gateway.digitalasset.application.DigitalAssetArtifactIngestionException;
import com.adp.gateway.digitalasset.application.DigitalAssetArtifactIngestionPersistence;
import com.adp.gateway.digitalasset.domain.DigitalAssetArtifactIngestion;
import com.adp.gateway.policy.domain.PolicyLifecycleStage;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class JdbcDigitalAssetArtifactIngestionPersistence implements DigitalAssetArtifactIngestionPersistence {
    private final JdbcClient jdbcClient;

    public JdbcDigitalAssetArtifactIngestionPersistence(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public void lockIdentity(String institutionId, String artifactId, String artifactVersion) {
        jdbcClient.sql("""
                select 1
                from (select pg_advisory_xact_lock(hashtextextended(:identity, 0))) acquired
                """)
            .param("identity", institutionId + "\u001f" + artifactId + "\u001f" + artifactVersion)
            .query(Integer.class)
            .single();
    }

    @Override
    public void updateRuntimeMetadata(
        String institutionId,
        String artifactId,
        String artifactVersion,
        String runtimeControlVersion,
        String runtimeControlDigest,
        String crosswalkVersion,
        String crosswalkDigest
    ) {
        jdbcClient.sql("""
                update policy.digital_asset_artifact_ingestion
                set runtime_control_version = :runtimeControlVersion,
                    runtime_control_digest = :runtimeControlDigest,
                    crosswalk_version = :crosswalkVersion,
                    crosswalk_digest = :crosswalkDigest
                where institution_id = :institutionId
                  and artifact_id = :artifactId and artifact_version = :artifactVersion
                """)
            .param("institutionId", institutionId)
            .param("artifactId", artifactId)
            .param("artifactVersion", artifactVersion)
            .param("runtimeControlVersion", runtimeControlVersion)
            .param("runtimeControlDigest", runtimeControlDigest)
            .param("crosswalkVersion", crosswalkVersion)
            .param("crosswalkDigest", crosswalkDigest)
            .update();
    }

    @Override
    public DigitalAssetArtifactIngestion create(DigitalAssetArtifactIngestion value) {
        try {
            jdbcClient.sql("""
                insert into policy.digital_asset_artifact_ingestion (
                    institution_id, artifact_id, artifact_version, artifact_digest,
                    manifest_schema_version, manifest_reference, canonical_contract_version,
                    canonical_contract_digest, workload_id, purpose_code, destination_profile_id,
                    runtime_control_version, runtime_control_digest, crosswalk_version, crosswalk_digest,
                    file_count, lifecycle_stage, ingested_by, ingested_at
                ) values (
                    :institutionId, :artifactId, :artifactVersion, :artifactDigest,
                    :manifestSchemaVersion, :manifestReference, :contractVersion,
                    :contractDigest, :workloadId, :purposeCode, :destinationProfileId,
                    :runtimeControlVersion, :runtimeControlDigest, :crosswalkVersion, :crosswalkDigest,
                    :fileCount, :lifecycleStage, :ingestedBy, :ingestedAt
                )
                """)
                .param("institutionId", value.institutionId())
                .param("artifactId", value.artifactId())
                .param("artifactVersion", value.artifactVersion())
                .param("artifactDigest", value.artifactDigest())
                .param("manifestSchemaVersion", value.manifestSchemaVersion())
                .param("manifestReference", value.manifestReference())
                .param("contractVersion", value.canonicalContractVersion())
                .param("contractDigest", value.canonicalContractDigest())
                .param("workloadId", value.workloadId())
                .param("purposeCode", value.purposeCode())
                .param("destinationProfileId", value.destinationProfileId())
                .param("runtimeControlVersion", value.runtimeControlVersion())
                .param("runtimeControlDigest", value.runtimeControlDigest())
                .param("crosswalkVersion", value.crosswalkVersion())
                .param("crosswalkDigest", value.crosswalkDigest())
                .param("fileCount", value.fileCount())
                .param("lifecycleStage", value.lifecycleStage().name())
                .param("ingestedBy", value.ingestedBy())
                .param("ingestedAt", value.ingestedAt())
                .update();
            return value;
        } catch (DuplicateKeyException exception) {
            throw new DigitalAssetArtifactIngestionException("DIGITAL_ASSET_ARTIFACT_CONFLICT", exception);
        }
    }

    @Override
    public Optional<DigitalAssetArtifactIngestion> find(
        String institutionId,
        Set<String> allowedWorkloads,
        String artifactId,
        String artifactVersion
    ) {
        if (allowedWorkloads == null || allowedWorkloads.isEmpty()) {
            return Optional.empty();
        }
        String workloadPredicate = allowedWorkloads.contains("*") ? "" : "and workload_id in (:workloads)";
        var query = jdbcClient.sql("""
                select institution_id, artifact_id, artifact_version, artifact_digest,
                       manifest_schema_version, manifest_reference, canonical_contract_version,
                       canonical_contract_digest, workload_id, purpose_code, destination_profile_id,
                       runtime_control_version, runtime_control_digest, crosswalk_version, crosswalk_digest,
                       file_count, lifecycle_stage, ingested_by, ingested_at
                from policy.digital_asset_artifact_ingestion
                where institution_id = :institutionId and artifact_id = :artifactId
                  and artifact_version = :artifactVersion
                """ + workloadPredicate)
            .param("institutionId", institutionId)
            .param("artifactId", artifactId)
            .param("artifactVersion", artifactVersion);
        if (!allowedWorkloads.contains("*")) {
            query = query.param("workloads", allowedWorkloads);
        }
        return query.query((rs, rowNum) -> new DigitalAssetArtifactIngestion(
            rs.getString("institution_id"), rs.getString("artifact_id"), rs.getString("artifact_version"),
            rs.getString("artifact_digest"), rs.getString("manifest_schema_version"),
            rs.getString("manifest_reference"), rs.getString("canonical_contract_version"),
            rs.getString("canonical_contract_digest"), rs.getString("workload_id"),
            rs.getString("purpose_code"), rs.getString("destination_profile_id"),
            rs.getString("runtime_control_version"), rs.getString("runtime_control_digest"),
            rs.getString("crosswalk_version"), rs.getString("crosswalk_digest"), rs.getInt("file_count"),
            PolicyLifecycleStage.valueOf(rs.getString("lifecycle_stage")), rs.getString("ingested_by"),
            rs.getObject("ingested_at", OffsetDateTime.class)
        )).optional();
    }
}
