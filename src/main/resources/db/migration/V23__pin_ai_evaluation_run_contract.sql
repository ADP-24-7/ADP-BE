alter table runtime.ai_model_execution_evidence
    add column evaluation_run_id varchar(120),
    add column evaluation_run_version varchar(80),
    add column eval_case_id varchar(120),
    add column evaluation_contract_digest varchar(71),
    add column expected_input_digest varchar(64),
    add column actual_input_digest varchar(64),
    add column dataset_id varchar(120),
    add column dataset_version varchar(120),
    add column dataset_digest varchar(160),
    add column policy_snapshot_digest varchar(160),
    add column destination_profile_digest varchar(160);

alter table runtime.ai_model_execution_evidence
    add constraint chk_ai_evaluation_binding_complete check (
        (evaluation_run_id is null and evaluation_run_version is null and eval_case_id is null
         and evaluation_contract_digest is null and expected_input_digest is null and actual_input_digest is null
         and dataset_id is null and dataset_version is null and dataset_digest is null
         and policy_snapshot_digest is null and destination_profile_digest is null)
        or
        (evaluation_run_id is not null and evaluation_run_version is not null and eval_case_id is not null
         and evaluation_contract_digest is not null and expected_input_digest is not null and actual_input_digest is not null
         and dataset_id is not null and dataset_version is not null and dataset_digest is not null
         and policy_snapshot_digest is not null and destination_profile_digest is not null)
    ),
    add constraint chk_ai_eval_contract_digest check (
        evaluation_contract_digest is null or evaluation_contract_digest ~ '^sha256:[0-9a-f]{64}$'
    ),
    add constraint chk_ai_eval_input_digests check (
        (expected_input_digest is null and actual_input_digest is null)
        or (expected_input_digest ~ '^[0-9a-f]{64}$' and actual_input_digest ~ '^[0-9a-f]{64}$'
            and expected_input_digest = actual_input_digest)
    ),
    add constraint chk_ai_eval_provenance_digests check (
        dataset_digest is null
        or (dataset_digest ~ '^sha256:[0-9a-f]{64}$'
            and policy_snapshot_digest ~ '^sha256:[0-9a-f]{64}$'
            and destination_profile_digest ~ '^sha256:[0-9a-f]{64}$')
    );
