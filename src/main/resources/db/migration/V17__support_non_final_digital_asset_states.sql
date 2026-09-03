alter table runtime.digital_asset_transaction
    alter column external_transaction_id drop not null,
    alter column settlement_id drop not null,
    alter column provider_response_digest drop not null;

alter table runtime.digital_asset_transaction
    add constraint chk_digital_asset_settled_evidence check (
        settlement_status <> 'SETTLED'
        or (
            external_transaction_id is not null
            and settlement_id is not null
            and provider_response_digest is not null
        )
    );
