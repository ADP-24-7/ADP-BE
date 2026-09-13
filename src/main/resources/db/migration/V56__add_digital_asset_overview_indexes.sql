create index idx_runtime_execution_digital_asset_overview
    on runtime.runtime_execution (institution_id, created_at desc, workload_id, status)
    where execution_pack = 'DIGITAL_ASSET';

create index idx_runtime_decision_digital_asset_overview
    on runtime.runtime_decision (execution_id, final_action);
