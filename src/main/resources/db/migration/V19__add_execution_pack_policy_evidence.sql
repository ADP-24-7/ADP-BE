create table runtime.execution_pack_policy_evaluation (
    id bigserial primary key,
    execution_id varchar(80) not null unique references runtime.runtime_execution (execution_id),
    pack_type varchar(40) not null,
    profile_id varchar(120) not null,
    profile_version varchar(120) not null,
    profile_digest varchar(64) not null,
    baseline_action varchar(40) not null,
    profile_action varchar(40) not null,
    final_action varchar(40) not null,
    reason_codes varchar(1000) not null,
    assertion_source varchar(120),
    assertion_version varchar(120),
    assertion_digest varchar(64),
    evaluated_at timestamptz not null,
    check (pack_type in ('COMMON', 'AI', 'DIGITAL_ASSET', 'SAAS')),
    check (baseline_action in ('ALLOW', 'TRANSFORM', 'REVIEW', 'BLOCK')),
    check (profile_action in ('ALLOW', 'TRANSFORM', 'REVIEW', 'BLOCK')),
    check (final_action in ('ALLOW', 'TRANSFORM', 'REVIEW', 'BLOCK')),
    check (profile_digest ~ '^[0-9a-f]{64}$'),
    check (assertion_digest is null or assertion_digest ~ '^[0-9a-f]{64}$')
);

create index idx_execution_pack_policy_profile
    on runtime.execution_pack_policy_evaluation (profile_id, profile_version, evaluated_at);
