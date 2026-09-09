alter table runtime.runtime_execution
    add column idempotency_retention_policy varchar(40) not null default 'LEGACY_INDEFINITE',
    add column idempotency_retention_seconds bigint,
    add column idempotency_expires_at timestamptz,
    add column idempotency_archived_at timestamptz;

alter table runtime.runtime_execution
    add constraint chk_runtime_idempotency_retention check (
        (idempotency_retention_policy = 'LEGACY_INDEFINITE'
            and idempotency_retention_seconds is null
            and idempotency_expires_at is null
            and idempotency_archived_at is null)
        or
        (idempotency_retention_policy = 'TERMINAL_TTL_V1'
            and idempotency_retention_seconds > 0
            and (idempotency_archived_at is null
                or (idempotency_expires_at is not null and idempotency_archived_at >= idempotency_expires_at)))
    );

drop index runtime.uq_runtime_execution_idempotency_scope;

create unique index uq_runtime_execution_active_idempotency_scope
    on runtime.runtime_execution (idempotency_institution_id, workload_id, idempotency_key)
    where idempotency_archived_at is null;

create index idx_runtime_execution_idempotency_expiry
    on runtime.runtime_execution (idempotency_expires_at, execution_id)
    where idempotency_archived_at is null and idempotency_expires_at is not null;
