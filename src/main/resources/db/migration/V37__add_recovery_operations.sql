create table runtime.recovery_operation_event (
    event_id varchar(80) primary key,
    operation_id varchar(120) not null,
    recovery_id varchar(80) not null references runtime.external_interaction_recovery (recovery_id),
    execution_id varchar(80) not null references runtime.runtime_execution (execution_id),
    institution_id varchar(120) not null,
    workload_id varchar(120) not null,
    actor_principal_id varchar(120) not null,
    operation_type varchar(40) not null,
    outcome varchar(40) not null,
    reason_code varchar(120),
    evidence_digest varchar(64),
    created_at timestamptz not null,
    completed_at timestamptz,
    unique (recovery_id, operation_id),
    check (operation_type in ('RECONCILE', 'RETRY', 'MARK_REVIEW')),
    check (outcome in ('IN_PROGRESS', 'SUCCEEDED', 'REJECTED', 'FAILED')),
    check (evidence_digest is null or evidence_digest ~ '^[0-9a-f]{64}$'),
    check (
        (outcome = 'IN_PROGRESS' and completed_at is null)
        or (outcome <> 'IN_PROGRESS' and completed_at is not null)
    )
);

create index idx_recovery_incident_scope
    on runtime.external_interaction_recovery (recovery_status, updated_at desc, recovery_id desc);

create index idx_recovery_operation_event_scope
    on runtime.recovery_operation_event (institution_id, workload_id, created_at desc);
