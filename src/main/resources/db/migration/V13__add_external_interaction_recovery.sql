create table runtime.external_interaction_recovery (
    recovery_id varchar(80) primary key,
    execution_id varchar(80) not null unique references runtime.runtime_execution (execution_id),
    connector_execution_id varchar(80) not null unique references runtime.connector_execution (connector_execution_id),
    connector_id varchar(120) not null,
    provider_correlation_key varchar(120) not null,
    observed_status varchar(40) not null,
    last_observed_external_status varchar(40),
    last_status_queried_at timestamptz,
    status_query_evidence_digest varchar(64),
    recovery_status varchar(40) not null,
    retry_disposition varchar(40) not null,
    attempt_count integer not null default 0,
    max_attempts integer not null,
    next_attempt_at timestamptz not null,
    lease_owner varchar(120),
    lease_until timestamptz,
    last_error_code varchar(120),
    created_at timestamptz not null,
    updated_at timestamptz not null,
    check (observed_status in ('NOT_SENT', 'SENT_UNKNOWN', 'ACKNOWLEDGED', 'COMPLETED', 'FAILED')),
    check (last_observed_external_status is null or last_observed_external_status in ('NOT_SENT', 'SENT_UNKNOWN', 'ACKNOWLEDGED', 'COMPLETED', 'FAILED')),
    check (recovery_status in ('PENDING', 'CLAIMED', 'RETRY_SCHEDULED', 'RECONCILED', 'MANUAL_REVIEW', 'EXHAUSTED')),
    check (retry_disposition in ('RETRY_ALLOWED', 'RECONCILE_FIRST', 'NO_RETRY', 'MANUAL_REVIEW')),
    check (attempt_count >= 0 and max_attempts > 0 and attempt_count <= max_attempts),
    check ((lease_owner is null and lease_until is null) or (lease_owner is not null and lease_until is not null))
);

alter table runtime.runtime_execution
    add constraint chk_runtime_execution_status_v13
    check (status in ('RECEIVED', 'AUTHORIZED', 'RETRIEVED', 'DECIDED', 'TRANSFORMED', 'EGRESSING', 'COMPLETED', 'REVIEW_REQUIRED', 'BLOCKED', 'FAILED', 'EXTERNALLY_RECONCILED'));

create index idx_external_interaction_recovery_due
    on runtime.external_interaction_recovery (recovery_status, next_attempt_at);
