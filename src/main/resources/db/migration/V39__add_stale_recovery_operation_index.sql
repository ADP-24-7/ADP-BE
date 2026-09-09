create index idx_recovery_operation_event_stale
    on runtime.recovery_operation_event (created_at)
    where outcome = 'IN_PROGRESS';
