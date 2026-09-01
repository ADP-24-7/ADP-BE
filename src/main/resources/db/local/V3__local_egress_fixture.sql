insert into egress.destination_profile (
    destination_profile_id,
    profile_version,
    profile_digest,
    contract_version,
    provider_profile_id,
    pack_type,
    schema_version,
    status,
    effective_at,
    expires_at,
    allowed_bindings,
    field_contracts,
    created_at
) values (
    'dest_internal_provider_project_provisional',
    '0.0.0',
    'local-fixture-destination-profile',
    'be-egress-contract/0.0.0',
    'internal-provider',
    'AI',
    'project-provisional-egress-schema-v1',
    'ACTIVE',
    timestamptz '2026-01-01T00:00:00Z',
    null,
    'customer_summary:CUSTOMER_SUPPORT',
    'project-provisional-field-contracts',
    timestamptz '2026-01-01T00:00:00Z'
) on conflict (destination_profile_id) do nothing;
