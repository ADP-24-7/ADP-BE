-- Synthetic, non-sensitive AI control activity for the local demo dashboard only.
-- Rows are refreshed into the latest 14-day window on every SYNTHETIC demo startup.
insert into auth_principal_workload (principal_id, workload_id)
select principal_id, workload_id
from (values ('operator-local'), ('privileged-operator-local'), ('auditor-local')) principals(principal_id)
cross join (values
    ('customer_summary'), ('document_analysis'), ('research_assistant'),
    ('marketing_content'), ('code_generation'), ('internal_knowledge')
) workloads(workload_id)
on conflict (principal_id, workload_id) do nothing;

delete from runtime.response_sensitive_finding where execution_id like 'exec_ai_dashboard_%';
delete from runtime.response_guard_result where execution_id like 'exec_ai_dashboard_%';
delete from runtime.ai_model_execution_evidence where execution_id like 'exec_ai_dashboard_%';
delete from runtime.connector_execution where execution_id like 'exec_ai_dashboard_%';
delete from runtime.outbound_candidate where execution_id like 'exec_ai_dashboard_%';
delete from runtime.transform_field where transform_execution_id like 'transform_ai_dash_%';
delete from runtime.transform_execution where execution_id like 'exec_ai_dashboard_%';
delete from runtime.runtime_decision where execution_id like 'exec_ai_dashboard_%';
delete from data_access_event where request_id like 'req_ai_dashboard_%';

with activity as (
    select n, (n * 37) % 100 as bucket,
           case when n <= 245 then (n - 1) % 7 else 7 + ((n - 246) % 7) end as day_offset,
           case when n % 20 < 6 then 'customer_summary'
                when n % 20 < 10 then 'document_analysis'
                when n % 20 < 14 then 'research_assistant'
                when n % 20 < 17 then 'marketing_content'
                when n % 20 < 19 then 'code_generation'
                else 'internal_knowledge' end as workload_id
    from generate_series(1, 560) n
), source as (
    select *, current_timestamp - day_offset * interval '1 day'
               - ((n * 7) % 24) * interval '1 hour'
               - ((n * 13) % 60) * interval '1 minute' as occurred_at
    from activity
)
insert into runtime.runtime_execution (
    execution_id, request_id, trace_id, idempotency_key, workload_id,
    idempotency_institution_id, request_hash, purpose_code, input_digest,
    institution_id, execution_pack, policy_version, final_action, status,
    connector_status, response_guard_status, requested_field_count,
    retrieved_field_count, transformed_field_count, released_field_count,
    created_at, updated_at
)
select 'exec_ai_dashboard_' || lpad(n::text, 4, '0'),
       'req_ai_dashboard_' || lpad(n::text, 4, '0'),
       'trace_ai_dashboard_' || lpad(n::text, 4, '0'),
       'idem_ai_dashboard_' || lpad(n::text, 4, '0'), workload_id,
       'institution_local', encode(sha256(('ai-request:' || n)::bytea), 'hex'),
       case workload_id when 'customer_summary' then 'CUSTOMER_SUPPORT'
            when 'document_analysis' then 'DOCUMENT_REVIEW'
            when 'research_assistant' then 'RESEARCH_SUPPORT'
            when 'marketing_content' then 'CONTENT_GENERATION'
            when 'code_generation' then 'CODE_ASSISTANCE' else 'INTERNAL_SEARCH' end,
       encode(sha256(('ai-input:' || n)::bytea), 'hex'), 'institution_local', 'AI',
       'be-runtime-policy/ai-control/1.1.0',
       case when bucket between 79 and 86 then 'BLOCK' when bucket between 70 and 78 then 'REVIEW' else 'TRANSFORM' end,
       case when bucket between 70 and 78 then 'REVIEW_REQUIRED'
            when bucket between 79 and 86 or bucket >= 97 then 'BLOCKED'
            when bucket between 87 and 91 then 'FAILED'
            when bucket between 92 and 96 then 'EGRESSING' else 'COMPLETED' end,
       case when bucket between 70 and 86 then null when bucket between 87 and 91 then 'FAILED'
            when bucket between 92 and 96 then 'SENT_UNKNOWN' else 'ACKNOWLEDGED' end,
       case when bucket between 70 and 96 then null when bucket >= 97 then 'REJECTED' else 'PASSED' end,
       5, case when bucket between 70 and 86 then null else 5 end,
       case when bucket between 70 and 86 then null else 3 end,
       case when bucket between 70 and 86 then null else 2 end,
       occurred_at, occurred_at
from source
on conflict (execution_id) do update set
    workload_id = excluded.workload_id, policy_version = excluded.policy_version,
    final_action = excluded.final_action, status = excluded.status,
    connector_status = excluded.connector_status, response_guard_status = excluded.response_guard_status,
    requested_field_count = excluded.requested_field_count, retrieved_field_count = excluded.retrieved_field_count,
    transformed_field_count = excluded.transformed_field_count, released_field_count = excluded.released_field_count,
    created_at = excluded.created_at, updated_at = excluded.updated_at;

with source as (
    select n, (n * 37) % 100 as bucket,
           current_timestamp - (case when n <= 245 then (n - 1) % 7 else 7 + ((n - 246) % 7) end) * interval '1 day'
               - ((n * 7) % 24) * interval '1 hour' - ((n * 13) % 60) * interval '1 minute' as occurred_at
    from generate_series(1, 560) n
)
insert into runtime.runtime_decision (
    execution_id, decision_id, policy_action, final_action, authorization_result,
    applicability_result, runtime_context_digest, reason_codes, created_at
)
select 'exec_ai_dashboard_' || lpad(n::text, 4, '0'), 'decision_ai_dashboard_' || lpad(n::text, 4, '0'),
       case when bucket between 79 and 86 then 'BLOCK' when bucket between 70 and 78 then 'REVIEW' else 'TRANSFORM' end,
       case when bucket between 79 and 86 then 'BLOCK' when bucket between 70 and 78 then 'REVIEW' else 'TRANSFORM' end,
       'ALLOWED', 'APPLICABLE', encode(sha256(('ai-context:' || n)::bytea), 'hex'),
       case when bucket between 79 and 86 then
           case bucket % 4 when 0 then 'UNAPPROVED_DATA_CLASS' when 1 then 'PURPOSE_SCOPE_MISMATCH'
                when 2 then 'EXTERNAL_DESTINATION_DENIED' else 'SENSITIVE_SCOPE_DENIED' end
            when bucket between 70 and 78 then 'HUMAN_REVIEW_REQUIRED' else 'POLICY_APPLICABLE' end,
       occurred_at
from source;

with source as (
    select n, (n * 37) % 100 as bucket,
           case when n % 20 < 6 then 'customer_summary' when n % 20 < 10 then 'document_analysis'
                when n % 20 < 14 then 'research_assistant' when n % 20 < 17 then 'marketing_content'
                when n % 20 < 19 then 'code_generation' else 'internal_knowledge' end as workload_id,
           current_timestamp - (case when n <= 245 then (n - 1) % 7 else 7 + ((n - 246) % 7) end) * interval '1 day'
               - ((n * 7) % 24) * interval '1 hour' - ((n * 13) % 60) * interval '1 minute' as occurred_at
    from generate_series(1, 560) n
)
insert into data_access_event (
    data_access_id, request_id, trace_id, workload_id, purpose, subject_type,
    subject_ref_digest, profile_id, selected_fields, row_count, created_at
)
select 'access_ai_dash_' || lpad(n::text, 4, '0'), 'req_ai_dashboard_' || lpad(n::text, 4, '0'),
       'trace_ai_dashboard_' || lpad(n::text, 4, '0'), workload_id, 'AUTHORIZED_AI_PROCESSING', 'synthetic-subject',
       encode(sha256(('synthetic-subject:' || n)::bytea), 'hex'), 'profile_ai_dashboard_v1',
       '["customer_ref","account_ref","amount","business_context","instructions"]', 1, occurred_at
from source where bucket < 70 or bucket >= 87;

with source as (
    select n, (n * 37) % 100 as bucket,
           current_timestamp - (case when n <= 245 then (n - 1) % 7 else 7 + ((n - 246) % 7) end) * interval '1 day'
               - ((n * 7) % 24) * interval '1 hour' - ((n * 13) % 60) * interval '1 minute' as occurred_at
    from generate_series(1, 560) n
)
insert into runtime.transform_execution (
    transform_execution_id, execution_id, decision_id, status, output_digest, field_count, created_at
)
select 'transform_ai_dash_' || lpad(n::text, 4, '0'), 'exec_ai_dashboard_' || lpad(n::text, 4, '0'),
       'decision_ai_dashboard_' || lpad(n::text, 4, '0'), 'COMPLETED',
       encode(sha256(('ai-transform:' || n)::bytea), 'hex'), 5, occurred_at
from source where bucket < 70 or bucket >= 87;

with source as (
    select n, (n * 37) % 100 as bucket,
           current_timestamp - (case when n <= 245 then (n - 1) % 7 else 7 + ((n - 246) % 7) end) * interval '1 day'
               - ((n * 7) % 24) * interval '1 hour' - ((n * 13) % 60) * interval '1 minute' as occurred_at
    from generate_series(1, 560) n
), fields(field_path, dataset_name, field_name, data_class, strategy) as (values
    ('$.customer_ref', 'customer', 'customer_ref', 'CUSTOMER_IDENTIFIER', 'VAULT_TOKEN'),
    ('$.account_ref', 'account', 'account_ref', 'ACCOUNT_IDENTIFIER', 'HMAC_PSEUDO'),
    ('$.amount', 'request', 'amount', 'FINANCIAL_AMOUNT', 'GENERALIZE'),
    ('$.business_context', 'request', 'business_context', 'BUSINESS_METADATA', 'KEEP'),
    ('$.instructions', 'request', 'instructions', 'BUSINESS_METADATA', 'KEEP')
)
insert into runtime.transform_field (
    transform_execution_id, field_path, dataset_name, field_name, data_class, strategy,
    strategy_version, key_version, mapping_version, instruction_digest,
    source_value_digest, transformed_value_digest, created_at
)
select 'transform_ai_dash_' || lpad(n::text, 4, '0'), field_path, dataset_name, field_name,
       data_class, strategy, 'demo-control/1.0.0', 'synthetic-key-v1', 'synthetic-map-v1',
       encode(sha256((field_path || ':instruction')::bytea), 'hex'),
       encode(sha256((field_path || ':source:' || n)::bytea), 'hex'),
       case when strategy = 'KEEP' then null else encode(sha256((field_path || ':transformed:' || n)::bytea), 'hex') end,
       occurred_at
from source cross join fields where bucket < 70 or bucket >= 87;

with source as (
    select n, (n * 37) % 100 as bucket,
           current_timestamp - (case when n <= 245 then (n - 1) % 7 else 7 + ((n - 246) % 7) end) * interval '1 day'
               - ((n * 7) % 24) * interval '1 hour' - ((n * 13) % 60) * interval '1 minute' as occurred_at
    from generate_series(1, 560) n
)
insert into runtime.outbound_candidate (
    outbound_payload_id, execution_id, destination_profile_id, destination_profile_version,
    destination_profile_digest, pack_type, schema_version, candidate_payload_digest,
    field_count, guard_status, guard_reason_codes, created_at
)
select 'outbound_ai_dash_' || lpad(n::text, 4, '0'), 'exec_ai_dashboard_' || lpad(n::text, 4, '0'),
       'dest_synthetic_ai_v1', '1.0.0', 'sha256:' || encode(sha256('dest_synthetic_ai_v1'::bytea), 'hex'),
       'AI', 'ai-outbound/v1', encode(sha256(('ai-outbound:' || n)::bytea), 'hex'), 2, 'PASSED', '', occurred_at
from source where bucket < 70 or bucket >= 87;

with source as (
    select n, (n * 37) % 100 as bucket,
           current_timestamp - (case when n <= 245 then (n - 1) % 7 else 7 + ((n - 246) % 7) end) * interval '1 day'
               - ((n * 7) % 24) * interval '1 hour' - ((n * 13) % 60) * interval '1 minute' as occurred_at
    from generate_series(1, 560) n
)
insert into runtime.connector_execution (
    connector_execution_id, execution_id, outbound_payload_id, outbound_candidate_digest,
    connector_id, status, response_digest, response_schema_version, created_at
)
select 'connector_ai_dash_' || lpad(n::text, 4, '0'), 'exec_ai_dashboard_' || lpad(n::text, 4, '0'),
       'outbound_ai_dash_' || lpad(n::text, 4, '0'), encode(sha256(('ai-outbound:' || n)::bytea), 'hex'),
       'synthetic-ai-provider', case when bucket between 87 and 91 then 'FAILED'
            when bucket between 92 and 96 then 'SENT_UNKNOWN' else 'ACKNOWLEDGED' end,
       case when bucket between 87 and 96 then null else encode(sha256(('ai-response:' || n)::bytea), 'hex') end,
       'ai-response/v1', occurred_at
from source where bucket < 70 or bucket >= 87;

with source as (
    select n, (n * 37) % 100 as bucket,
           current_timestamp - (case when n <= 245 then (n - 1) % 7 else 7 + ((n - 246) % 7) end) * interval '1 day'
               - ((n * 7) % 24) * interval '1 hour' - ((n * 13) % 60) * interval '1 minute' as occurred_at
    from generate_series(1, 560) n
)
insert into runtime.ai_model_execution_evidence (
    execution_id, profile_id, profile_version, profile_digest, provider_model_id,
    provider_model_version, connection_profile_id, max_tokens, temperature,
    sampling_profile_version, recorded_at, measurement_type, full_response_latency_ms,
    attempt_elapsed_ms, token_usage_status, provider_status, error_category,
    provider_http_status, evidence_status
)
select 'exec_ai_dashboard_' || lpad(n::text, 4, '0'), 'synthetic-ai-profile', '1.0.0',
       'sha256:' || encode(sha256('synthetic-ai-profile'::bytea), 'hex'),
       case n % 3 when 0 then 'safe-summary-model' when 1 then 'document-assistant-model' else 'controlled-generation-model' end,
       'demo-1', 'synthetic-connection-v1', 512, 0.2, 'controlled-sampling-v1', occurred_at,
       case when bucket between 87 and 96 then 'HTTP_ATTEMPT_NO_RESPONSE' else 'HTTP_FULL_RESPONSE' end,
       case when bucket between 87 and 96 then null else 650 + ((n * 97) % 2100) end,
       case when bucket between 87 and 96 then 300 + ((n * 41) % 900) else null end,
       'NOT_PROVIDED', case when bucket between 87 and 91 then 'FAILED'
            when bucket between 92 and 96 then 'SENT_UNKNOWN' else 'COMPLETED' end,
       case when bucket between 87 and 91 then 'TRANSPORT' else 'NONE' end,
       case when bucket between 87 and 91 then 503 when bucket between 92 and 96 then null else 200 end,
       case when bucket between 87 and 96 then 'PARTIAL' else 'COMPLETE' end
from source where bucket < 70 or bucket >= 87;

with source as (
    select n, (n * 37) % 100 as bucket,
           current_timestamp - (case when n <= 245 then (n - 1) % 7 else 7 + ((n - 246) % 7) end) * interval '1 day'
               - ((n * 7) % 24) * interval '1 hour' - ((n * 13) % 60) * interval '1 minute' as occurred_at
    from generate_series(1, 560) n
)
insert into runtime.response_guard_result (
    connector_execution_id, execution_id, connector_id, connector_status, status,
    leakage_detected, reason_codes, response_digest, detector_version, finding_count, created_at
)
select 'connector_ai_dash_' || lpad(n::text, 4, '0'), 'exec_ai_dashboard_' || lpad(n::text, 4, '0'),
       'synthetic-ai-provider', 'ACKNOWLEDGED', case when bucket >= 97 then 'REJECTED' else 'PASSED' end,
       bucket >= 97, case when bucket >= 97 then 'SENSITIVE_DATA_REFLECTION' else 'NO_SENSITIVE_DATA' end,
       encode(sha256(('ai-response:' || n)::bytea), 'hex'), 'synthetic-detector/1.0.0',
       case when bucket >= 97 then 1 else 0 end, occurred_at
from source where bucket < 70 or bucket >= 97;

with source as (
    select n, current_timestamp - (case when n <= 245 then (n - 1) % 7 else 7 + ((n - 246) % 7) end) * interval '1 day'
               - ((n * 7) % 24) * interval '1 hour' - ((n * 13) % 60) * interval '1 minute' as occurred_at
    from generate_series(1, 560) n where (n * 37) % 100 >= 97
)
insert into runtime.response_sensitive_finding (
    connector_execution_id, execution_id, finding_type, location, start_offset, end_offset,
    detector_version, evidence_digest, source_data_class, transform_strategy,
    field_treatment, outbound_field_path_digest, created_at
)
select 'connector_ai_dash_' || lpad(n::text, 4, '0'), 'exec_ai_dashboard_' || lpad(n::text, 4, '0'),
       'RAW_VALUE_REFLECTION', '$.response.summary', 18, 30, 'synthetic-detector/1.0.0',
       encode(sha256(('synthetic-finding:' || n)::bytea), 'hex'),
       case n % 3 when 0 then 'CUSTOMER_IDENTIFIER' when 1 then 'ACCOUNT_IDENTIFIER' else 'FINANCIAL_AMOUNT' end,
       'VAULT_TOKEN', 'TRANSFORMED', encode(sha256(('outbound-path:' || n)::bytea), 'hex'), occurred_at
from source;
