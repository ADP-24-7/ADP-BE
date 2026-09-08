create table policy.digital_asset_artifact_ingestion (
    institution_id varchar(120) not null,
    artifact_id varchar(120) not null,
    artifact_version varchar(120) not null,
    artifact_digest varchar(64) not null,
    manifest_schema_version varchar(120) not null,
    manifest_reference varchar(512) not null,
    canonical_contract_version varchar(120) not null,
    canonical_contract_digest varchar(71) not null,
    workload_id varchar(120) not null,
    purpose_code varchar(120) not null,
    destination_profile_id varchar(120) not null,
    file_count integer not null,
    lifecycle_stage varchar(40) not null,
    ingested_by varchar(120) not null,
    ingested_at timestamptz not null,
    primary key (institution_id, artifact_id, artifact_version),
    foreign key (institution_id, artifact_id, artifact_version)
        references policy.lifecycle_artifact (institution_id, artifact_id, artifact_version),
    constraint chk_da_artifact_digest check (artifact_digest ~ '^[0-9a-f]{64}$'),
    constraint chk_da_artifact_contract_digest check (
        canonical_contract_digest ~ '^sha256:[0-9a-f]{64}$'
    ),
    constraint chk_da_artifact_file_count check (file_count = 5),
    constraint chk_da_artifact_lifecycle check (lifecycle_stage = 'CANDIDATE')
);

create index idx_da_artifact_ingestion_scope
    on policy.digital_asset_artifact_ingestion (
        institution_id, workload_id, purpose_code, ingested_at desc
    );
