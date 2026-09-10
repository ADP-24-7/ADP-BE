-- Applied after the policy operations read indexes introduced on main.
create table runtime.ai_evaluation_contract (
    evaluation_run_id varchar(120) primary key,
    fixed_conditions_digest varchar(71) not null check (fixed_conditions_digest ~ '^sha256:[0-9a-f]{64}$'),
    snapshot_json text not null,
    frozen_at timestamptz not null default current_timestamp,
    unique (evaluation_run_id, fixed_conditions_digest)
);

create table runtime.ai_evaluation_case_input (
    evaluation_run_id varchar(120) not null references runtime.ai_evaluation_contract(evaluation_run_id),
    eval_case_id varchar(120) not null,
    provider_input_digest varchar(71) not null,
    primary key (evaluation_run_id, eval_case_id),
    unique (evaluation_run_id, eval_case_id, provider_input_digest)
);

create table runtime.ai_evaluation_contract_binding (
    execution_id varchar(120) primary key references runtime.runtime_execution(execution_id),
    evaluation_run_id varchar(120) not null,
    eval_case_id varchar(120) not null,
    fixed_conditions_digest varchar(71) not null,
    model_profile_digest varchar(71) not null,
    decision_id varchar(120) not null,
    transform_execution_id varchar(120) not null,
    outbound_payload_id varchar(120) not null,
    provider_request_digest varchar(71) not null,
    provider_input_digest varchar(71) not null,
    foreign key (evaluation_run_id, eval_case_id, provider_input_digest)
        references runtime.ai_evaluation_case_input(evaluation_run_id, eval_case_id, provider_input_digest),
    foreign key (evaluation_run_id, fixed_conditions_digest)
        references runtime.ai_evaluation_contract(evaluation_run_id, fixed_conditions_digest)
);

create function runtime.reject_ai_evaluation_snapshot_mutation() returns trigger language plpgsql as $$
begin
    raise exception 'AI evaluation snapshots and bindings are immutable';
end;
$$;
create trigger ai_contract_immutable before update or delete on runtime.ai_evaluation_contract
    for each row execute function runtime.reject_ai_evaluation_snapshot_mutation();
create trigger ai_case_input_immutable before update or delete on runtime.ai_evaluation_case_input
    for each row execute function runtime.reject_ai_evaluation_snapshot_mutation();
create trigger ai_contract_binding_immutable before update or delete on runtime.ai_evaluation_contract_binding
    for each row execute function runtime.reject_ai_evaluation_snapshot_mutation();
