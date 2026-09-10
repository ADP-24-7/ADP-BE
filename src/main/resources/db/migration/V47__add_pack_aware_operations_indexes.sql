create index idx_runtime_execution_operations_pack_window
    on runtime.runtime_execution (
        institution_id, execution_pack, created_at desc, workload_id
    )
    where execution_pack is not null;
