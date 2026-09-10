create index idx_da_artifact_current_state_scope
    on policy.digital_asset_artifact_ingestion (
        institution_id, workload_id, ingested_at desc, artifact_id, artifact_version
    );

create index idx_da_runtime_snapshot_artifact_history
    on runtime.digital_asset_runtime_snapshot (
        institution_id, artifact_id, artifact_version, selected_at desc, snapshot_id desc
    );
