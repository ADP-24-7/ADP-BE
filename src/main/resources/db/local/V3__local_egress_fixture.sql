insert into egress.destination_profile (
    destination_profile_id,
    provider_profile_id,
    pack_type,
    schema_version,
    enabled,
    allowed_workloads,
    allowed_purposes,
    created_at
) values (
    'dest_internal_provider_project_provisional',
    'internal-provider',
    'AI',
    'project-provisional-egress-schema-v1',
    true,
    'customer_summary',
    'CUSTOMER_SUPPORT',
    timestamptz '2026-01-01T00:00:00Z'
) on conflict (destination_profile_id) do nothing;
