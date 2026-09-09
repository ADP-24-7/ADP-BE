create table policy.current_selection (
    institution_id varchar(120) not null,
    policy_layer varchar(40) not null,
    execution_pack varchar(40) not null,
    workload_id varchar(120) not null,
    purpose_code varchar(120) not null,
    artifact_id varchar(120) not null,
    artifact_version varchar(120) not null,
    artifact_digest varchar(64) not null,
    artifact_revision bigint not null,
    selection_revision bigint not null,
    selected_by varchar(120) not null,
    selected_at timestamptz not null,
    primary key (institution_id, execution_pack, workload_id, purpose_code),
    foreign key (institution_id, artifact_id, artifact_version)
        references policy.lifecycle_artifact (institution_id, artifact_id, artifact_version),
    constraint chk_policy_selection_digest check (artifact_digest ~ '^[0-9a-f]{64}$'),
    constraint chk_policy_selection_layer check (
        policy_layer in ('REGULATORY_BASELINE', 'INSTITUTION', 'WORKLOAD', 'DESTINATION')
    ),
    constraint chk_policy_selection_pack check (execution_pack in ('COMMON', 'AI', 'DIGITAL_ASSET')),
    constraint chk_policy_selection_revisions check (artifact_revision >= 0 and selection_revision >= 0)
);

create table policy.current_selection_event (
    selection_event_id bigserial primary key,
    institution_id varchar(120) not null,
    policy_layer varchar(40) not null,
    execution_pack varchar(40) not null,
    workload_id varchar(120) not null,
    purpose_code varchar(120) not null,
    event_type varchar(20) not null,
    previous_artifact_id varchar(120),
    previous_artifact_version varchar(120),
    previous_artifact_digest varchar(64),
    selected_artifact_id varchar(120) not null,
    selected_artifact_version varchar(120) not null,
    selected_artifact_digest varchar(64) not null,
    selected_artifact_revision bigint not null,
    selection_revision bigint not null,
    actor_id varchar(120) not null,
    reason_code varchar(40) not null,
    occurred_at timestamptz not null,
    foreign key (institution_id, selected_artifact_id, selected_artifact_version)
        references policy.lifecycle_artifact (institution_id, artifact_id, artifact_version),
    constraint chk_policy_selection_event_type check (event_type in ('ACTIVATED', 'ROLLED_BACK')),
    constraint chk_policy_selection_event_reason check (
        reason_code in ('ACTIVATION_APPROVED', 'ROLLBACK_APPROVED')
    ),
    constraint chk_policy_selection_event_digest check (
        selected_artifact_digest ~ '^[0-9a-f]{64}$'
        and (previous_artifact_digest is null or previous_artifact_digest ~ '^[0-9a-f]{64}$')
    ),
    constraint chk_policy_selection_event_previous check (
        (previous_artifact_id is null and previous_artifact_version is null and previous_artifact_digest is null)
        or
        (previous_artifact_id is not null and previous_artifact_version is not null and previous_artifact_digest is not null)
    )
);

create index idx_policy_current_selection_artifact
    on policy.current_selection (institution_id, artifact_id, artifact_version);

create index idx_policy_current_selection_event_scope
    on policy.current_selection_event (
        institution_id, execution_pack, workload_id, purpose_code, occurred_at desc
    );

alter table policy.lifecycle_transition_event
    drop constraint chk_policy_transition_reason;

alter table policy.lifecycle_transition_event
    add constraint chk_policy_transition_reason check (reason_code in (
        'VALIDATION_PASSED', 'CANDIDATE_PROMOTED', 'REPLAY_PASSED', 'SHADOW_PASSED',
        'APPROVAL_GRANTED', 'ACTIVATION_APPROVED', 'ROLLBACK_RESTORED',
        'ACTIVE_VERSION_SUPERSEDED', 'REVIEW_OPENED', 'ROLLBACK_APPROVED'
    ));

alter table runtime.policy_evaluation
    add column current_selection_revision bigint,
    add column selected_artifact_revision bigint,
    add column selected_policy_layer varchar(40),
    add constraint chk_runtime_policy_current_selection check (
        (current_selection_revision is null and selected_artifact_revision is null and selected_policy_layer is null)
        or
        (current_selection_revision >= 0 and selected_artifact_revision >= 0
            and selected_policy_layer in ('REGULATORY_BASELINE', 'INSTITUTION', 'WORKLOAD', 'DESTINATION'))
    );
