create schema if not exists evidence;

create table evidence.reference_evidence_bundle (
    institution_id varchar(120) not null,
    bundle_id varchar(120) not null,
    bundle_version varchar(40) not null,
    schema_version varchar(80) not null,
    analysis_version varchar(120) not null,
    content_digest varchar(71) not null,
    snapshot_at timestamptz not null,
    evidence_count integer not null check (evidence_count > 0 and evidence_count <= 1000),
    ingested_by varchar(120) not null,
    ingested_at timestamptz not null,
    primary key (institution_id, bundle_id, bundle_version),
    check (content_digest ~ '^sha256:[0-9a-f]{64}$')
);

create table evidence.reference_evidence (
    institution_id varchar(120) not null,
    evidence_id varchar(80) not null,
    evidence_version varchar(40) not null,
    bundle_id varchar(120) not null,
    bundle_version varchar(40) not null,
    evidence_type varchar(40) not null,
    authority varchar(160) not null,
    title varchar(240) not null,
    source_ref varchar(500) not null,
    source_url varchar(1000),
    source_date date,
    effective_from date,
    effective_to date,
    claim_scope varchar(500) not null,
    claim_summary varchar(1000) not null,
    source_locator varchar(500) not null,
    analysis_version varchar(120) not null,
    status varchar(40) not null,
    content_digest varchar(71) not null,
    created_at timestamptz not null,
    primary key (institution_id, evidence_id, evidence_version),
    foreign key (institution_id, bundle_id, bundle_version)
        references evidence.reference_evidence_bundle (institution_id, bundle_id, bundle_version),
    check (evidence_type in ('REGULATION', 'REGULATORY_SANDBOX', 'POLICY_GUIDE', 'BANK_TREND', 'DIGITAL_ASSET_INFRA')),
    check (status in ('VERIFIED', 'REFERENCE_ONLY', 'REVIEW_REQUIRED', 'SUPERSEDED')),
    check (content_digest ~ '^sha256:[0-9a-f]{64}$'),
    check (effective_from is null or effective_to is null or effective_from <= effective_to)
);

create table evidence.reference_evidence_workload (
    institution_id varchar(120) not null,
    evidence_id varchar(80) not null,
    evidence_version varchar(40) not null,
    workload_id varchar(120) not null,
    primary key (institution_id, evidence_id, evidence_version, workload_id),
    foreign key (institution_id, evidence_id, evidence_version)
        references evidence.reference_evidence (institution_id, evidence_id, evidence_version)
);

create table evidence.reference_evidence_policy_artifact (
    institution_id varchar(120) not null,
    evidence_id varchar(80) not null,
    evidence_version varchar(40) not null,
    policy_artifact_ref varchar(120) not null,
    primary key (institution_id, evidence_id, evidence_version, policy_artifact_ref),
    foreign key (institution_id, evidence_id, evidence_version)
        references evidence.reference_evidence (institution_id, evidence_id, evidence_version)
);

create index idx_reference_evidence_search
    on evidence.reference_evidence (institution_id, evidence_type, status, created_at desc);
create index idx_reference_evidence_workload
    on evidence.reference_evidence_workload (institution_id, workload_id, evidence_id, evidence_version);
create index idx_reference_evidence_policy
    on evidence.reference_evidence_policy_artifact (institution_id, policy_artifact_ref, evidence_id, evidence_version);
