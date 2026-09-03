create table runtime.digital_asset_transaction (
    id bigserial primary key,
    execution_id varchar(80) not null unique references runtime.runtime_execution (execution_id),
    external_request_id varchar(120) not null unique,
    external_transaction_id varchar(120) not null unique,
    settlement_id varchar(120) not null unique,
    settlement_status varchar(40) not null,
    reconciliation_result varchar(40) not null,
    provider_response_digest varchar(64) not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint chk_digital_asset_settlement_status check (settlement_status in (
        'REQUESTED', 'POLICY_APPROVED', 'READY_TO_SUBMIT', 'SUBMITTED', 'SETTLING', 'SETTLED',
        'FAILED', 'SENT_UNKNOWN', 'RECONCILIATION_REQUIRED'
    )),
    constraint chk_digital_asset_reconciliation check (reconciliation_result in (
        'MATCH', 'WAIT', 'RECOVERED', 'MISMATCH', 'CRITICAL_MISMATCH'
    ))
);

create index idx_digital_asset_transaction_settlement
    on runtime.digital_asset_transaction (settlement_status, updated_at);
