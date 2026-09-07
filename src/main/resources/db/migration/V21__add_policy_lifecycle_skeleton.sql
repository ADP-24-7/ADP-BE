create schema if not exists policy;

create table policy.lifecycle_artifact (
    artifact_id varchar(120) not null,
    artifact_version varchar(120) not null,
    artifact_digest varchar(64) not null,
    institution_id varchar(120) not null,
    policy_layer varchar(40) not null,
    execution_pack varchar(40) not null,
    workload_id varchar(120) not null,
    purpose_code varchar(120) not null,
    lifecycle_stage varchar(40) not null,
    created_by varchar(120) not null,
    revision bigint not null default 0,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    primary key (artifact_id, artifact_version),
    constraint chk_policy_lifecycle_digest check (artifact_digest ~ '^[0-9a-f]{64}$'),
    constraint chk_policy_lifecycle_layer check (
        policy_layer in ('REGULATORY_BASELINE', 'INSTITUTION', 'WORKLOAD', 'DESTINATION')
    ),
    constraint chk_policy_lifecycle_pack check (execution_pack in ('COMMON', 'AI', 'DIGITAL_ASSET')),
    constraint chk_policy_lifecycle_stage check (lifecycle_stage in (
        'DRAFT', 'VALIDATED', 'CANDIDATE', 'REPLAY', 'SHADOW',
        'APPROVED', 'ACTIVE', 'REVIEW', 'ROLLED_BACK'
    ))
);

create table policy.lifecycle_transition_event (
    transition_id bigserial primary key,
    artifact_id varchar(120) not null,
    artifact_version varchar(120) not null,
    from_stage varchar(40) not null,
    to_stage varchar(40) not null,
    actor_id varchar(120) not null,
    reason_code varchar(40) not null,
    artifact_digest varchar(64) not null,
    occurred_at timestamptz not null,
    foreign key (artifact_id, artifact_version)
        references policy.lifecycle_artifact (artifact_id, artifact_version),
    constraint chk_policy_transition_digest check (artifact_digest ~ '^[0-9a-f]{64}$'),
    constraint chk_policy_transition_not_same check (from_stage <> to_stage),
    constraint chk_policy_transition_reason check (reason_code in (
        'VALIDATION_PASSED', 'CANDIDATE_PROMOTED', 'REPLAY_PASSED', 'SHADOW_PASSED',
        'APPROVAL_GRANTED', 'ACTIVATION_APPROVED', 'REVIEW_OPENED', 'ROLLBACK_APPROVED'
    ))
);

create index idx_policy_lifecycle_scope
    on policy.lifecycle_artifact (institution_id, workload_id, lifecycle_stage, updated_at);

create index idx_policy_transition_artifact
    on policy.lifecycle_transition_event (artifact_id, artifact_version, occurred_at);
