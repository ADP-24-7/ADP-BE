create table runtime.request_attempt (
    attempt_id varchar(80) primary key,
    request_id varchar(80) not null,
    trace_id varchar(80) not null,
    client_trace_id_digest varchar(64),
    principal_id varchar(120) not null,
    institution_id varchar(120),
    workload_id varchar(120) not null,
    purpose_code varchar(160) not null,
    subject_ref_digest varchar(64),
    authorization_result varchar(20) not null,
    reason_code varchar(60) not null,
    created_at timestamptz not null,
    constraint chk_request_attempt_authorization check (authorization_result = 'DENIED'),
    constraint chk_request_attempt_client_trace_digest check (
        client_trace_id_digest is null or client_trace_id_digest ~ '^[0-9a-f]{64}$'
    ),
    constraint chk_request_attempt_subject_digest check (
        subject_ref_digest is null or subject_ref_digest ~ '^[0-9a-f]{64}$'
    ),
    constraint chk_request_attempt_reason check (
        reason_code in ('INSTITUTION_SCOPE_MISMATCH', 'AUTHORIZATION_POLICY_DENIED')
    )
);

create index idx_request_attempt_institution_created
    on runtime.request_attempt (institution_id, created_at desc);

create index idx_request_attempt_principal_created
    on runtime.request_attempt (principal_id, created_at desc);
