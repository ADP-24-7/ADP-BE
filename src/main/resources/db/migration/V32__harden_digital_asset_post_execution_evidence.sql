alter table runtime.digital_asset_post_execution_evidence
    add column evidence_source_type varchar(32) not null default 'PROVIDER_RESPONSE';

alter table runtime.digital_asset_post_execution_evidence
    add constraint chk_da_post_execution_source_type check (
        evidence_source_type in ('PROVIDER_RESPONSE', 'INDEPENDENT_EXTERNAL')
    );

alter table runtime.digital_asset_post_execution_evidence
    drop constraint chk_da_post_execution_verified;

alter table runtime.digital_asset_post_execution_evidence
    add constraint chk_da_post_execution_verified check (
        status <> 'VERIFIED'
        or (
            evidence_source_type = 'INDEPENDENT_EXTERNAL'
            and external_status = 'SETTLED'
            and receipt_status = 'SUCCESS'
            and finality_status = 'FINALIZED'
            and transaction_detail_digest is not null
            and receipt_finality_digest is not null
            and transfer_evidence_digest is not null
            and exact_amount_digest is not null
            and mismatch_fields = '[]'::jsonb
        )
    );
