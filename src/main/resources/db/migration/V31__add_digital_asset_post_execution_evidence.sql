create table runtime.digital_asset_post_execution_evidence (
    execution_id varchar(80) primary key
        references runtime.digital_asset_pre_execution_guard (execution_id),
    status varchar(24) not null,
    external_status varchar(40) not null,
    provider_status varchar(24) not null,
    receipt_status varchar(24) not null,
    finality_status varchar(24) not null,
    amount_source varchar(32) not null,
    transaction_detail_digest varchar(64) not null,
    receipt_finality_digest varchar(64) not null,
    transfer_evidence_digest varchar(64) not null,
    internal_trace_evidence_digest varchar(64),
    exact_amount_digest varchar(64) not null,
    expected_projection_digest varchar(64) not null,
    actual_projection_digest varchar(64) not null,
    mismatch_fields jsonb not null,
    provider_response_digest varchar(64) not null,
    observed_at timestamptz not null,
    constraint chk_da_post_execution_status check (
        status in ('VERIFIED', 'PENDING', 'SENT_UNKNOWN', 'REVIEW_REQUIRED', 'FAILED')
    ),
    constraint chk_da_post_execution_amount_source check (
        amount_source in ('TRANSACTION_VALUE', 'TOKEN_TRANSFER')
    ),
    constraint chk_da_post_execution_mismatch_array check (jsonb_typeof(mismatch_fields) = 'array'),
    constraint chk_da_post_execution_verified check (
        status <> 'VERIFIED'
        or (external_status = 'SETTLED' and receipt_status = 'SUCCESS' and finality_status = 'FINALIZED')
    )
);

create index idx_da_post_execution_status
    on runtime.digital_asset_post_execution_evidence (status, observed_at desc);
