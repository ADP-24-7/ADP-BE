alter table runtime.ai_model_execution_evidence
    add column evaluation_run_id varchar(120),
    add column evaluation_run_version varchar(80),
    add column eval_case_id varchar(120),
    add column dataset_id varchar(120),
    add column dataset_version varchar(120),
    add column dataset_digest varchar(160),
    add column policy_snapshot_digest varchar(160),
    add column destination_profile_digest varchar(160);

alter table runtime.ai_model_execution_evidence
    add constraint chk_ai_evaluation_binding_complete check (
        (evaluation_run_id is null and evaluation_run_version is null and eval_case_id is null
         and dataset_id is null and dataset_version is null and dataset_digest is null
         and policy_snapshot_digest is null and destination_profile_digest is null)
        or
        (evaluation_run_id is not null and evaluation_run_version is not null and eval_case_id is not null
         and dataset_id is not null and dataset_version is not null and dataset_digest is not null
         and policy_snapshot_digest is not null and destination_profile_digest is not null)
    );
