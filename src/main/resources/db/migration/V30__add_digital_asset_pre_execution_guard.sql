create table runtime.digital_asset_pre_execution_guard (
    execution_id varchar(80) primary key references runtime.runtime_execution (execution_id),
    snapshot_id varchar(80) not null references runtime.digital_asset_runtime_snapshot (snapshot_id),
    status varchar(24) not null,
    control_results jsonb not null,
    reason_codes jsonb not null,
    outbound_payload_digest varchar(200) not null,
    provider_payload_digest varchar(200) not null,
    evaluated_at timestamptz not null,
    constraint chk_da_pre_execution_status check (status in ('PASSED', 'BLOCKED', 'REVIEW_REQUIRED')),
    constraint chk_da_pre_execution_controls_object check (jsonb_typeof(control_results) = 'object'),
    constraint chk_da_pre_execution_reasons_array check (jsonb_typeof(reason_codes) = 'array')
);

create index idx_da_pre_execution_guard_status
    on runtime.digital_asset_pre_execution_guard (status, evaluated_at desc);
