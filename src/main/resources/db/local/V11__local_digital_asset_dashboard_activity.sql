-- Persisted local operational activity for the Digital Asset dashboard.
-- These rows stay in PostgreSQL and are refreshed into the latest 14-day window on each demo startup.
with activity as (
    select n,
           case when n <= 245 then (n - 1) % 7 else 7 + ((n - 246) % 7) end as day_offset,
           n % 100 as bucket
    from generate_series(1, 420) n
), source as (
    select n, bucket,
           current_timestamp - day_offset * interval '1 day'
               - ((n * 7) % 24) * interval '1 hour'
               - ((n * 13) % 60) * interval '1 minute' as occurred_at,
           case when bucket < 68 then 'COMPLETED'
                when bucket < 83 then 'BLOCKED'
                when bucket < 88 then 'REVIEW_REQUIRED'
                when bucket < 92 then 'FAILED'
                when bucket < 96 then 'EGRESSING'
                else 'EXTERNALLY_RECONCILED' end as runtime_status
    from activity
)
insert into runtime.runtime_execution (
    execution_id, request_id, trace_id, idempotency_key, workload_id,
    idempotency_institution_id, request_hash, purpose_code, input_digest,
    institution_id, execution_pack, policy_version, final_action, status,
    connector_status, destination_profile_id, destination_profile_version,
    destination_profile_digest, created_at, updated_at
)
select 'exec_da_dashboard_' || lpad(n::text, 4, '0'),
       'req_da_dashboard_' || to_char(occurred_at at time zone 'Asia/Seoul', 'YYYYMMDD') || '_' || lpad(n::text, 4, '0'),
       'trace_da_dashboard_' || lpad(n::text, 4, '0'),
       'idem_da_dashboard_' || lpad(n::text, 4, '0'),
       'tokenized_asset_purchase', 'institution_local',
       encode(sha256(('request:' || n)::bytea), 'hex'), 'DIGITAL_ASSET_PURCHASE',
       encode(sha256(('input:' || n)::bytea), 'hex'), 'institution_local', 'DIGITAL_ASSET',
       'be-runtime-policy/digital-asset/1.0.0',
       case when runtime_status = 'BLOCKED' then 'BLOCK' else 'TRANSFORM' end,
       runtime_status,
       case when runtime_status = 'BLOCKED' then 'NOT_SENT'
            when runtime_status = 'FAILED' then 'FAILED'
            when runtime_status = 'EGRESSING' then 'SENT_UNKNOWN'
            else 'ACKNOWLEDGED' end,
       'dest_mock_asset_platform_v1', '1.0.0',
       'sha256:' || encode(sha256('dest_mock_asset_platform_v1'::bytea), 'hex'),
       occurred_at, occurred_at
from source
on conflict (execution_id) do update set
    request_id = excluded.request_id,
    final_action = excluded.final_action,
    status = excluded.status,
    connector_status = excluded.connector_status,
    created_at = excluded.created_at,
    updated_at = excluded.updated_at;

with source as (
    select n, n % 100 as bucket,
           current_timestamp - (case when n <= 245 then (n - 1) % 7 else 7 + ((n - 246) % 7) end) * interval '1 day'
               - ((n * 7) % 24) * interval '1 hour'
               - ((n * 13) % 60) * interval '1 minute' as occurred_at
    from generate_series(1, 420) n
)
insert into runtime.runtime_decision (
    execution_id, decision_id, policy_action, final_action, authorization_result,
    applicability_result, runtime_context_digest, reason_codes, created_at
)
select 'exec_da_dashboard_' || lpad(n::text, 4, '0'),
       'decision_da_dashboard_' || lpad(n::text, 4, '0'),
       case when bucket between 68 and 82 then 'BLOCK' else 'TRANSFORM' end,
       case when bucket between 68 and 82 then 'BLOCK' else 'TRANSFORM' end,
       'ALLOWED', 'APPLICABLE', encode(sha256(('context:' || n)::bytea), 'hex'),
       case when bucket between 68 and 82 then
           case bucket % 5
               when 0 then 'DIGITAL_ASSET_APPROVED_AMOUNT_EXCEEDED'
               when 1 then 'DIGITAL_ASSET_APPROVED_DESTINATION_MISMATCH'
               when 2 then 'DIGITAL_ASSET_APPROVED_ASSET_MISMATCH'
               when 3 then 'DIGITAL_ASSET_APPROVED_BENEFICIARY_MISMATCH'
               else 'DIGITAL_ASSET_APPROVED_PERIOD_VIOLATION' end
           else 'POLICY_APPLICABLE' end,
       occurred_at
from source
where not exists (
    select 1 from runtime.runtime_decision existing
    where existing.execution_id = 'exec_da_dashboard_' || lpad(source.n::text, 4, '0')
);

with source as (
    select n, n % 100 as bucket,
           current_timestamp - (case when n <= 245 then (n - 1) % 7 else 7 + ((n - 246) % 7) end) * interval '1 day'
               - ((n * 7) % 24) * interval '1 hour'
               - ((n * 13) % 60) * interval '1 minute' as occurred_at
    from generate_series(1, 420) n
)
insert into runtime.digital_asset_transaction (
    execution_id, external_request_id, external_transaction_id, settlement_id,
    settlement_status, reconciliation_result, provider_response_digest, created_at, updated_at
)
select 'exec_da_dashboard_' || lpad(n::text, 4, '0'),
       'external_req_da_' || lpad(n::text, 4, '0'),
       'external_tx_da_' || lpad(n::text, 4, '0'),
       'settlement_da_' || lpad(n::text, 4, '0'),
       case when bucket < 68 or bucket between 83 and 87 or bucket >= 96 then 'SETTLED'
            when bucket < 92 then 'FAILED'
            when bucket < 96 then 'SENT_UNKNOWN'
            else 'SETTLING' end,
       case when bucket < 68 then 'MATCH'
            when bucket between 83 and 87 then 'MISMATCH'
            when bucket >= 96 then 'RECOVERED'
            else 'WAIT' end,
       encode(sha256(('provider-response:' || n)::bytea), 'hex'), occurred_at, occurred_at
from source
where bucket < 68 or bucket >= 83
on conflict (execution_id) do update set
    settlement_status = excluded.settlement_status,
    reconciliation_result = excluded.reconciliation_result,
    created_at = excluded.created_at,
    updated_at = excluded.updated_at;

with source as (
    select n,
           current_timestamp - (case when n <= 245 then (n - 1) % 7 else 7 + ((n - 246) % 7) end) * interval '1 day'
               - ((n * 7) % 24) * interval '1 hour'
               - ((n * 13) % 60) * interval '1 minute' as occurred_at
    from generate_series(1, 420) n
    where n % 100 between 83 and 87
)
insert into runtime.digital_asset_mismatch_case (
    execution_id, severity, mismatched_fields, expected_projection_digest,
    actual_projection_digest, case_status, auto_retry_allowed, opened_at, updated_at
)
select 'exec_da_dashboard_' || lpad(n::text, 4, '0'), 'MISMATCH',
       case when n % 2 = 0 then '["AMOUNT"]'::jsonb else '["WALLET_ADDRESS"]'::jsonb end,
       encode(sha256(('expected:' || n)::bytea), 'hex'),
       encode(sha256(('actual:' || n)::bytea), 'hex'),
       'OPEN', false, occurred_at, occurred_at
from source
on conflict (execution_id) do update set
    mismatched_fields = excluded.mismatched_fields,
    opened_at = excluded.opened_at,
    updated_at = excluded.updated_at;

with source as (
    select n,
           current_timestamp - (case when n <= 245 then (n - 1) % 7 else 7 + ((n - 246) % 7) end) * interval '1 day'
               - ((n * 7) % 24) * interval '1 hour'
               - ((n * 13) % 60) * interval '1 minute' as occurred_at
    from generate_series(1, 420) n
    where n % 100 >= 92
)
insert into runtime.outbound_candidate (
    outbound_payload_id, execution_id, destination_profile_id, destination_profile_version,
    destination_profile_digest, pack_type, schema_version, candidate_payload_digest,
    field_count, guard_status, guard_reason_codes, created_at
)
select 'outbound_da_dashboard_' || lpad(n::text, 4, '0'),
       'exec_da_dashboard_' || lpad(n::text, 4, '0'),
       'dest_mock_asset_platform_v1', '1.0.0',
       'sha256:' || encode(sha256('dest_mock_asset_platform_v1'::bytea), 'hex'),
       'DIGITAL_ASSET', 'digital-asset-outbound/v1',
       encode(sha256(('outbound:' || n)::bytea), 'hex'), 6, 'PASSED', '', occurred_at
from source
on conflict (outbound_payload_id) do update set created_at = excluded.created_at;

with source as (
    select n, n % 100 as bucket,
           current_timestamp - (case when n <= 245 then (n - 1) % 7 else 7 + ((n - 246) % 7) end) * interval '1 day'
               - ((n * 7) % 24) * interval '1 hour'
               - ((n * 13) % 60) * interval '1 minute' as occurred_at
    from generate_series(1, 420) n
    where n % 100 >= 92
)
insert into runtime.connector_execution (
    connector_execution_id, execution_id, outbound_payload_id, outbound_candidate_digest,
    connector_id, status, response_digest, response_schema_version, created_at
)
select 'connector_da_dashboard_' || lpad(n::text, 4, '0'),
       'exec_da_dashboard_' || lpad(n::text, 4, '0'),
       'outbound_da_dashboard_' || lpad(n::text, 4, '0'),
       encode(sha256(('outbound:' || n)::bytea), 'hex'), 'mock-asset-platform',
       case when bucket < 96 then 'SENT_UNKNOWN' else 'COMPLETED' end,
       encode(sha256(('connector-response:' || n)::bytea), 'hex'),
       'digital-asset-response/v1', occurred_at
from source
on conflict (connector_execution_id) do update set
    status = excluded.status,
    created_at = excluded.created_at;

with source as (
    select n, n % 100 as bucket,
           current_timestamp - (case when n <= 245 then (n - 1) % 7 else 7 + ((n - 246) % 7) end) * interval '1 day'
               - ((n * 7) % 24) * interval '1 hour'
               - ((n * 13) % 60) * interval '1 minute' as occurred_at
    from generate_series(1, 420) n
    where n % 100 >= 92
)
insert into runtime.external_interaction_recovery (
    recovery_id, execution_id, connector_execution_id, connector_id,
    provider_correlation_key, observed_status, last_observed_external_status,
    last_status_queried_at, status_query_evidence_digest, recovery_status,
    retry_disposition, attempt_count, max_attempts, next_attempt_at,
    last_error_code, created_at, updated_at
)
select 'recovery_da_dashboard_' || lpad(n::text, 4, '0'),
       'exec_da_dashboard_' || lpad(n::text, 4, '0'),
       'connector_da_dashboard_' || lpad(n::text, 4, '0'), 'mock-asset-platform',
       'provider-key-da-' || lpad(n::text, 4, '0'),
       case when bucket < 96 then 'SENT_UNKNOWN' else 'COMPLETED' end,
       case when bucket < 96 then 'SENT_UNKNOWN' else 'COMPLETED' end,
       occurred_at, encode(sha256(('status-query:' || n)::bytea), 'hex'),
       case when bucket < 96 then 'PENDING' else 'RECONCILED' end,
       case when bucket < 96 then 'RECONCILE_FIRST' else 'NO_RETRY' end,
       case when bucket < 96 then 1 else 2 end, 3, occurred_at + interval '5 minutes',
       case when bucket < 96 then 'SENT_UNKNOWN' else null end,
       occurred_at, occurred_at
from source
on conflict (recovery_id) do update set
    observed_status = excluded.observed_status,
    recovery_status = excluded.recovery_status,
    next_attempt_at = excluded.next_attempt_at,
    created_at = excluded.created_at,
    updated_at = excluded.updated_at;
