insert into policy.lifecycle_artifact (
    artifact_id, artifact_version, artifact_digest, institution_id, policy_layer,
    execution_pack, workload_id, purpose_code, lifecycle_stage, created_by,
    revision, created_at, updated_at
) values (
    'DA-DIGITAL-ASSET-RUNTIME-LOCAL-ACTIVE-001', '1.0.0',
    '2a0fad69b2db5f8e18081fadbdb71e5c0af2c12ea436e25276f5b8fa693d468f',
    'institution_local', 'WORKLOAD', 'DIGITAL_ASSET', 'tokenized_asset_purchase',
    'DIGITAL_ASSET_PURCHASE', 'ACTIVE', 'local-fixture-maker', 6,
    timestamptz '2026-01-01T00:00:00Z', timestamptz '2026-01-01T00:00:00Z'
) on conflict (institution_id, artifact_id, artifact_version) do nothing;

insert into policy.digital_asset_artifact_ingestion (
    institution_id, artifact_id, artifact_version, artifact_digest,
    manifest_schema_version, manifest_reference, canonical_contract_version,
    canonical_contract_digest, workload_id, purpose_code, destination_profile_id,
    runtime_control_version, runtime_control_digest, crosswalk_version, crosswalk_digest,
    file_count, lifecycle_stage, ingested_by, ingested_at
) values (
    'institution_local', 'DA-DIGITAL-ASSET-RUNTIME-LOCAL-ACTIVE-001', '1.0.0',
    '2a0fad69b2db5f8e18081fadbdb71e5c0af2c12ea436e25276f5b8fa693d468f',
    'adp-digital-asset-artifact-bundle/v1',
    'docs/contracts/artifacts/p0-5-sample/manifest.json', '1.0.0',
    'sha256:886842702fad124a95e73f0f714ffacef56d5cbd5a4c0f222440279d106b3504',
    'tokenized_asset_purchase', 'DIGITAL_ASSET_PURCHASE', 'dest_mock_asset_platform_v1',
    '1.0.0', 'sha256:94447f7910fa799caa4613dc80bc588104d5829e2042963affd98825bdd2c43c',
    '1.0.0', 'sha256:eb40822cdd5c192e68eb0fe3a961428dbabbdabda00204c760d32cac6b669cf5',
    5, 'CANDIDATE', 'local-fixture-maker', timestamptz '2026-01-01T00:00:00Z'
) on conflict (institution_id, artifact_id, artifact_version) do update set
    runtime_control_version = excluded.runtime_control_version,
    runtime_control_digest = excluded.runtime_control_digest,
    crosswalk_version = excluded.crosswalk_version,
    crosswalk_digest = excluded.crosswalk_digest;

insert into policy.digital_asset_active_artifact (
    institution_id, workload_id, purpose_code, artifact_id, artifact_version,
    artifact_digest, activated_by, activated_at
) values (
    'institution_local', 'tokenized_asset_purchase', 'DIGITAL_ASSET_PURCHASE',
    'DA-DIGITAL-ASSET-RUNTIME-LOCAL-ACTIVE-001', '1.0.0',
    '2a0fad69b2db5f8e18081fadbdb71e5c0af2c12ea436e25276f5b8fa693d468f',
    'local-fixture-checker', timestamptz '2026-01-01T00:00:00Z'
) on conflict (institution_id, workload_id) do nothing;
