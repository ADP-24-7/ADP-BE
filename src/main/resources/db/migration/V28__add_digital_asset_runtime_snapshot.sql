alter table policy.digital_asset_artifact_ingestion
    add column runtime_control_version varchar(120),
    add column runtime_control_digest varchar(71),
    add column crosswalk_version varchar(120),
    add column crosswalk_digest varchar(71),
    add constraint chk_da_artifact_runtime_control_digest check (
        runtime_control_digest is null or runtime_control_digest ~ '^sha256:[0-9a-f]{64}$'
    ),
    add constraint chk_da_artifact_crosswalk_digest check (
        crosswalk_digest is null or crosswalk_digest ~ '^sha256:[0-9a-f]{64}$'
    );

create table policy.digital_asset_active_artifact (
    institution_id varchar(120) not null,
    workload_id varchar(120) not null,
    purpose_code varchar(120) not null,
    artifact_id varchar(120) not null,
    artifact_version varchar(120) not null,
    artifact_digest varchar(64) not null,
    activated_by varchar(120) not null,
    activated_at timestamptz not null,
    primary key (institution_id, workload_id),
    foreign key (institution_id, artifact_id, artifact_version)
        references policy.digital_asset_artifact_ingestion (institution_id, artifact_id, artifact_version),
    constraint chk_da_active_artifact_digest check (artifact_digest ~ '^[0-9a-f]{64}$')
);

create table runtime.digital_asset_runtime_snapshot (
    snapshot_id varchar(80) primary key,
    snapshot_digest varchar(71) not null unique,
    execution_id varchar(80) not null unique references runtime.runtime_execution (execution_id),
    institution_id varchar(120) not null,
    workload_id varchar(120) not null,
    purpose_code varchar(120) not null,
    artifact_id varchar(120) not null,
    artifact_version varchar(120) not null,
    artifact_digest varchar(64) not null,
    approved_policy_snapshot_id varchar(240) not null,
    approved_policy_version varchar(120) not null,
    approved_policy_digest varchar(200) not null,
    destination_profile_id varchar(120) not null,
    destination_profile_version varchar(120) not null,
    destination_profile_digest varchar(200) not null,
    runtime_control_version varchar(120) not null,
    runtime_control_digest varchar(71) not null,
    crosswalk_version varchar(120) not null,
    crosswalk_digest varchar(71) not null,
    selected_at timestamptz not null,
    foreign key (institution_id, artifact_id, artifact_version)
        references policy.digital_asset_artifact_ingestion (institution_id, artifact_id, artifact_version),
    constraint chk_da_runtime_snapshot_digest check (snapshot_digest ~ '^sha256:[0-9a-f]{64}$'),
    constraint chk_da_runtime_snapshot_artifact_digest check (artifact_digest ~ '^[0-9a-f]{64}$'),
    constraint chk_da_runtime_snapshot_control_digest check (runtime_control_digest ~ '^sha256:[0-9a-f]{64}$'),
    constraint chk_da_runtime_snapshot_crosswalk_digest check (crosswalk_digest ~ '^sha256:[0-9a-f]{64}$')
);

create index idx_da_runtime_snapshot_scope
    on runtime.digital_asset_runtime_snapshot (institution_id, workload_id, selected_at desc);
