create table runtime.execution_pack_policy_evaluation (
    id bigserial primary key,
    execution_id varchar(80) not null unique references runtime.runtime_execution (execution_id),
    pack_type varchar(40) not null,
    profile_id varchar(120) not null,
    profile_version varchar(120) not null,
    profile_digest varchar(64) not null,
    result varchar(40) not null,
    reason_codes varchar(1000) not null,
    evaluated_at timestamptz not null,
    check (pack_type in ('AI', 'DIGITAL_ASSET', 'SAAS')),
    check (result in ('ALLOW', 'TRANSFORM', 'REVIEW', 'BLOCK')),
    check (profile_digest ~ '^[0-9a-f]{64}$')
);

create index idx_execution_pack_policy_profile
    on runtime.execution_pack_policy_evaluation (profile_id, profile_version, evaluated_at);
