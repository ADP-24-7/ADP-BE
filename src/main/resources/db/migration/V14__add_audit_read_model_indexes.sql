create index idx_runtime_execution_audit_search
    on runtime.runtime_execution (institution_id, created_at desc, execution_id desc);

create index idx_runtime_execution_audit_status_search
    on runtime.runtime_execution (institution_id, status, created_at desc);

create index idx_runtime_execution_audit_workload_search
    on runtime.runtime_execution (institution_id, workload_id, created_at desc);
