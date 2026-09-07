create table runtime.ai_model_execution_evidence (
    execution_id varchar(120) primary key references runtime.runtime_execution (execution_id),
    profile_id varchar(160) not null,
    profile_version varchar(80) not null,
    profile_digest varchar(71) not null,
    provider_model_id varchar(200) not null,
    provider_model_version varchar(80) not null,
    connection_profile_id varchar(120) not null,
    max_tokens integer not null,
    temperature numeric(8, 4) not null,
    sampling_profile_version varchar(80) not null,
    recorded_at timestamptz not null,
    constraint chk_ai_model_profile_digest check (profile_digest ~ '^sha256:[0-9a-f]{64}$'),
    constraint chk_ai_model_max_tokens check (max_tokens > 0),
    constraint chk_ai_model_temperature check (temperature >= 0)
);
