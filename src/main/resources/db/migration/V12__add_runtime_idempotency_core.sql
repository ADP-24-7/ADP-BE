alter table runtime.runtime_execution
    add column idempotency_institution_id varchar(120),
    add column request_hash varchar(64);

update runtime.runtime_execution
set idempotency_institution_id = coalesce(institution_id, 'LEGACY_UNSCOPED'),
    request_hash = encode(sha256(('legacy:' || execution_id)::bytea), 'hex');

alter table runtime.runtime_execution
    alter column idempotency_institution_id set not null,
    alter column request_hash set not null;

drop index runtime.uq_runtime_execution_workload_idempotency;

create unique index uq_runtime_execution_idempotency_scope
    on runtime.runtime_execution (idempotency_institution_id, workload_id, idempotency_key);

create index idx_runtime_execution_request_hash
    on runtime.runtime_execution (request_hash);
