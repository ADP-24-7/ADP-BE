create table runtime_execution (
    id bigserial primary key,
    execution_id varchar(80) not null unique,
    request_id varchar(80) not null,
    trace_id varchar(80) not null,
    idempotency_key varchar(120) not null,
    workload_id varchar(120) not null,
    purpose_code varchar(160) not null,
    subject_ref_digest varchar(64),
    provider_profile_id varchar(120) not null,
    canonical_context_digest varchar(64),
    runtime_context_digest varchar(64),
    policy_version varchar(120),
    snapshot_digest varchar(200),
    decision_id varchar(120),
    final_action varchar(40),
    status varchar(40) not null,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create table policy_snapshot (
    id bigserial primary key,
    policy_version varchar(120) not null,
    snapshot_digest varchar(200) not null unique,
    lifecycle_stage varchar(40) not null,
    effective_at timestamptz,
    source_artifact_id varchar(120) not null,
    source_artifact_version varchar(120) not null,
    source_artifact_digest_algorithm varchar(40) not null,
    source_artifact_digest_value varchar(200) not null,
    policy_action varchar(40) not null,
    matched_policy_refs varchar(2000) not null,
    matched_rule_refs varchar(2000) not null,
    requirement_refs varchar(2000) not null,
    evidence_refs varchar(2000) not null,
    required_controls varchar(2000) not null,
    validation_artifact_refs varchar(2000) not null,
    created_at timestamptz not null
);

create table runtime_policy_evaluation (
    id bigserial primary key,
    execution_id varchar(80) not null references runtime_execution (execution_id),
    policy_version varchar(120) not null,
    snapshot_digest varchar(200) not null,
    source_artifact_id varchar(120) not null,
    source_artifact_version varchar(120) not null,
    source_artifact_digest_algorithm varchar(40) not null,
    source_artifact_digest_value varchar(200) not null,
    policy_action varchar(40) not null,
    matched_rule_refs varchar(2000) not null,
    evidence_refs varchar(2000) not null,
    required_controls varchar(2000) not null,
    created_at timestamptz not null
);

create table runtime_decision (
    id bigserial primary key,
    execution_id varchar(80) not null references runtime_execution (execution_id),
    decision_id varchar(120) not null,
    policy_action varchar(40) not null,
    final_action varchar(40) not null,
    authorization_result varchar(40) not null,
    applicability_result varchar(40) not null,
    runtime_context_digest varchar(64) not null,
    reason_codes varchar(500) not null,
    created_at timestamptz not null
);

create index idx_runtime_execution_request_id on runtime_execution (request_id);
create index idx_runtime_execution_trace_id on runtime_execution (trace_id);
create index idx_runtime_execution_scope on runtime_execution (workload_id, purpose_code, provider_profile_id);
create index idx_policy_snapshot_source_artifact on policy_snapshot (source_artifact_id, source_artifact_version);
create index idx_runtime_decision_decision_id on runtime_decision (decision_id);
