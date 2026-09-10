create table audit_export_job (
    export_id varchar(80) primary key,
    institution_id varchar(120) not null,
    workload_id varchar(120) not null,
    execution_pack varchar(40) not null,
    execution_id varchar(80) not null references runtime.runtime_execution (execution_id),
    report_type varchar(40) not null,
    export_format varchar(10) not null,
    status varchar(24) not null,
    scope_digest varchar(64) not null,
    requester_id varchar(120) not null,
    approver_id varchar(120),
    request_reason varchar(500) not null,
    approval_reason varchar(500),
    idempotency_key varchar(120) not null,
    scope_json varchar(2000) not null,
    row_count integer,
    content bytea,
    content_digest varchar(64),
    content_size bigint,
    content_type varchar(120),
    file_name varchar(180),
    lease_owner varchar(120),
    lease_until timestamptz,
    failure_code varchar(80),
    created_at timestamptz not null,
    approved_at timestamptz,
    generated_at timestamptz,
    expires_at timestamptz,
    downloaded_at timestamptz,
    updated_at timestamptz not null,
    version bigint not null default 0,
    constraint uq_audit_export_idempotency unique (institution_id, requester_id, idempotency_key),
    constraint chk_audit_export_pack check (execution_pack in ('COMMON', 'AI', 'DIGITAL_ASSET', 'SAAS')),
    constraint chk_audit_export_format check (export_format in ('CSV', 'PDF')),
    constraint chk_audit_export_report_type check (report_type in ('EXECUTION_EVIDENCE')),
    constraint chk_audit_export_status check (status in (
        'REQUESTED', 'APPROVED', 'GENERATING', 'READY', 'REJECTED', 'FAILED', 'EXPIRED', 'REVOKED'
    )),
    constraint chk_audit_export_maker_checker check (approver_id is null or approver_id <> requester_id),
    constraint chk_audit_export_content check (
        (status = 'READY' and content is not null and content_digest is not null
            and content_size is not null and content_type is not null and file_name is not null
            and generated_at is not null and expires_at is not null)
        or status <> 'READY'
    )
);

create table audit_export_event (
    event_id varchar(80) primary key,
    export_id varchar(80) not null references audit_export_job (export_id),
    institution_id varchar(120) not null,
    actor_id varchar(120) not null,
    request_id varchar(80),
    trace_id varchar(80),
    action varchar(40) not null,
    from_status varchar(24),
    to_status varchar(24) not null,
    reason_code varchar(80) not null,
    occurred_at timestamptz not null
);

create index idx_audit_export_job_scope
    on audit_export_job (institution_id, workload_id, execution_pack, created_at desc);
create index idx_audit_export_job_claim
    on audit_export_job (status, approved_at, created_at)
    where status in ('APPROVED', 'GENERATING');
create index idx_audit_export_event_history
    on audit_export_event (export_id, occurred_at, event_id);
