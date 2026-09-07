create index idx_ai_model_execution_evidence_evaluation_run
    on runtime.ai_model_execution_evidence (evaluation_run_id, eval_case_id, profile_id, execution_id)
    where evaluation_run_id is not null;
