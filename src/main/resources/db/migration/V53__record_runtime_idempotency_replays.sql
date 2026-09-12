alter table runtime.runtime_execution
    add column idempotency_replay_count integer not null default 0,
    add constraint chk_runtime_execution_idempotency_replay_count
        check (idempotency_replay_count >= 0);
