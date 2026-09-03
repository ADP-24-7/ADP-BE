insert into auth_principal_workload (principal_id, workload_id) values
    ('svc_local_runtime', 'tokenized_asset_purchase')
on conflict (principal_id, workload_id) do nothing;

insert into auth_subject_grant (
    principal_id, workload_id, action_name, purpose, subject_type, subject_id
) values (
    'svc_local_runtime', 'tokenized_asset_purchase', 'RUNTIME_EXECUTE',
    'DIGITAL_ASSET_PURCHASE', 'customer', 'customer-100'
) on conflict (principal_id, workload_id, action_name, purpose, subject_type, subject_id) do nothing;

insert into workload_registry (workload_id, display_name, description, enabled) values (
    'tokenized_asset_purchase', 'Tokenized Asset Purchase',
    'Input-only local fixture for the Digital Asset Thin E2E.', true
) on conflict (workload_id) do nothing;

insert into retrieval_profile (profile_id, workload_id, purpose, subject_type, enabled) values (
    'profile_tokenized_asset_purchase', 'tokenized_asset_purchase',
    'DIGITAL_ASSET_PURCHASE', 'customer', true
) on conflict (profile_id) do nothing;

insert into retrieval_profile_dataset (profile_id, dataset_name, row_limit, time_window_days) values
    ('profile_tokenized_asset_purchase', 'request', 1, null)
on conflict (profile_id, dataset_name) do nothing;

insert into retrieval_profile_field (profile_id, dataset_name, field_name, data_class) values
    ('profile_tokenized_asset_purchase', 'request', 'customerId', 'CUSTOMER_IDENTIFIER'),
    ('profile_tokenized_asset_purchase', 'request', 'accountId', 'ACCOUNT_IDENTIFIER'),
    ('profile_tokenized_asset_purchase', 'request', 'walletAddress', 'TRANSACTION_IDENTIFIER'),
    ('profile_tokenized_asset_purchase', 'request', 'assetId', 'BUSINESS_METADATA'),
    ('profile_tokenized_asset_purchase', 'request', 'amount', 'FINANCIAL_AMOUNT'),
    ('profile_tokenized_asset_purchase', 'request', 'kycStatus', 'FINANCIAL_METADATA'),
    ('profile_tokenized_asset_purchase', 'request', 'amlStatus', 'FINANCIAL_METADATA'),
    ('profile_tokenized_asset_purchase', 'request', 'walletVerified', 'FINANCIAL_METADATA')
on conflict (profile_id, dataset_name, field_name) do nothing;

insert into egress.destination_profile (
    destination_profile_id, profile_version, profile_digest, contract_version,
    provider_profile_id, pack_type, schema_version, status, effective_at, expires_at,
    allowed_bindings, field_contracts, created_at
) values (
    'dest_mock_asset_platform_v1', '1.0.0', 'local-digital-asset-destination-v1',
    'digital-asset-egress-contract/v1', 'mock-asset-platform', 'DIGITAL_ASSET',
    'digital-asset-request/v1', 'ACTIVE', timestamptz '2026-01-01T00:00:00Z', null,
    'tokenized_asset_purchase:DIGITAL_ASSET_PURCHASE', 'digital-asset-field-contracts-v1',
    timestamptz '2026-01-01T00:00:00Z'
) on conflict (destination_profile_id) do nothing;
