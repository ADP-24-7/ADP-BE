create table evidence.reference_evidence_policy_artifact (
    institution_id varchar(120) not null,
    evidence_id varchar(80) not null,
    evidence_version varchar(40) not null,
    artifact_id varchar(120) not null,
    artifact_version varchar(120) not null,
    source_digest varchar(71) not null,
    binding_type varchar(40) not null default 'REGULATORY_BASIS',
    bound_by varchar(120) not null,
    bound_at timestamptz not null,
    primary key (
        institution_id, evidence_id, evidence_version, artifact_id, artifact_version
    ),
    foreign key (institution_id, evidence_id, evidence_version)
        references evidence.reference_evidence (institution_id, evidence_id, evidence_version),
    foreign key (institution_id, artifact_id, artifact_version)
        references policy.lifecycle_artifact (institution_id, artifact_id, artifact_version),
    constraint chk_reference_evidence_policy_binding_type
        check (binding_type = 'REGULATORY_BASIS'),
    constraint chk_reference_evidence_policy_source_digest
        check (source_digest ~ '^sha256:[0-9a-f]{64}$')
);

create index idx_reference_evidence_policy_artifact_lookup
    on evidence.reference_evidence_policy_artifact (
        institution_id, artifact_id, artifact_version, evidence_id, evidence_version
    );
