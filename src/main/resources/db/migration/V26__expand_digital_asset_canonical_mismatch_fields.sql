alter table runtime.digital_asset_mismatch_case
    drop constraint chk_digital_asset_mismatch_fields;

alter table runtime.digital_asset_mismatch_case
    add constraint chk_digital_asset_mismatch_fields
        check (jsonb_typeof(mismatched_fields) = 'array'
            and jsonb_array_length(mismatched_fields) > 0
            and mismatched_fields <@ '["CUSTOMER_TOKEN", "ACCOUNT_TOKEN", "WALLET_ADDRESS", "ASSET_ID",
                "AMOUNT", "KYC_STATUS", "AML_STATUS", "WALLET_VERIFIED", "CHAIN_ID",
                "RECIPIENT_ADDRESS", "ASSET_KIND", "ASSET_SYMBOL", "ASSET_CONTRACT_ADDRESS",
                "OPERATION", "TOKEN_ID", "EXTERNAL_REQUEST_ID", "UNEXPECTED_FIELD"]'::jsonb);
