create index idx_runtime_execution_ai_operations_overview
    on runtime.runtime_execution (institution_id, created_at desc, workload_id, status)
    where execution_pack = 'AI';

create index idx_ai_model_execution_overview_latency
    on runtime.ai_model_execution_evidence (recorded_at, execution_id)
    where full_response_latency_ms is not null;
