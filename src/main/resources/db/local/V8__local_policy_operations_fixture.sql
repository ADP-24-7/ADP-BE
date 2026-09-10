insert into policy.lifecycle_artifact (
    artifact_id,
    artifact_version,
    artifact_digest,
    institution_id,
    policy_layer,
    execution_pack,
    workload_id,
    purpose_code,
    lifecycle_stage,
    created_by,
    revision,
    created_at,
    updated_at
) values (
    'AI-POLICY-LOCAL-VALIDATED-001',
    '1.0.0',
    repeat('8', 64),
    'institution_local',
    'WORKLOAD',
    'AI',
    'customer_summary',
    'CUSTOMER_SUPPORT',
    'VALIDATED',
    'local-policy-maker',
    1,
    timestamptz '2026-09-09 00:00:00+00',
    timestamptz '2026-09-09 00:00:00+00'
)
on conflict (institution_id, artifact_id, artifact_version) do nothing;

insert into policy.lifecycle_transition_event (
    institution_id,
    artifact_id,
    artifact_version,
    from_stage,
    to_stage,
    actor_id,
    reason_code,
    artifact_digest,
    occurred_at,
    approval_gate_version
)
select
    'institution_local',
    'AI-POLICY-LOCAL-VALIDATED-001',
    '1.0.0',
    'DRAFT',
    'VALIDATED',
    'local-policy-maker',
    'VALIDATION_PASSED',
    repeat('8', 64),
    timestamptz '2026-09-09 00:00:00+00',
    'NOT_APPLICABLE'
where not exists (
    select 1
    from policy.lifecycle_transition_event
    where institution_id = 'institution_local'
      and artifact_id = 'AI-POLICY-LOCAL-VALIDATED-001'
      and artifact_version = '1.0.0'
      and from_stage = 'DRAFT'
      and to_stage = 'VALIDATED'
      and actor_id = 'local-policy-maker'
);
