create table runtime.digital_asset_mismatch_case (
    mismatch_case_id bigserial primary key,
    execution_id varchar(80) not null unique references runtime.runtime_execution (execution_id),
    severity varchar(40) not null,
    mismatched_fields jsonb not null,
    expected_payload_digest varchar(64) not null,
    actual_payload_digest varchar(64) not null,
    case_status varchar(30) not null,
    auto_retry_allowed boolean not null default false,
    opened_at timestamptz not null,
    updated_at timestamptz not null,
    constraint chk_digital_asset_mismatch_severity
        check (severity in ('MISMATCH', 'CRITICAL_MISMATCH')),
    constraint chk_digital_asset_mismatch_status
        check (case_status in ('OPEN', 'UNDER_REVIEW', 'RESOLVED', 'REJECTED')),
    constraint chk_digital_asset_mismatch_no_auto_retry
        check (auto_retry_allowed = false),
    constraint chk_digital_asset_mismatch_digests
        check (expected_payload_digest ~ '^[0-9a-f]{64}$' and actual_payload_digest ~ '^[0-9a-f]{64}$'),
    constraint chk_digital_asset_mismatch_fields
        check (jsonb_typeof(mismatched_fields) = 'array' and jsonb_array_length(mismatched_fields) > 0)
);

create index idx_digital_asset_mismatch_case_review
    on runtime.digital_asset_mismatch_case (case_status, severity, opened_at);
