alter table runtime.ai_model_execution_evidence
    add column measurement_type varchar(40),
    add column full_response_latency_ms bigint,
    add column attempt_elapsed_ms bigint,
    add column initial_runtime_latency_ms bigint,
    add column input_tokens integer,
    add column output_tokens integer,
    add column total_tokens integer,
    add column token_usage_status varchar(40),
    add column provider_status varchar(40),
    add column error_category varchar(80);

alter table runtime.ai_model_execution_evidence
    add constraint chk_ai_runtime_evidence_timing check (
        (measurement_type is null and full_response_latency_ms is null and attempt_elapsed_ms is null)
        or (measurement_type = 'HTTP_FULL_RESPONSE' and full_response_latency_ms >= 0 and attempt_elapsed_ms is null)
        or (measurement_type = 'HTTP_ATTEMPT_TIMEOUT' and full_response_latency_ms is null and attempt_elapsed_ms >= 0)
        or (measurement_type = 'NOT_ATTEMPTED' and full_response_latency_ms is null and attempt_elapsed_ms is null)
        or (measurement_type = 'MOCK' and full_response_latency_ms = 0 and attempt_elapsed_ms is null)
    ),
    add constraint chk_ai_initial_runtime_latency check (
        initial_runtime_latency_ms is null or initial_runtime_latency_ms >= 0
    ),
    add constraint chk_ai_runtime_evidence_tokens check (
        (token_usage_status in ('NOT_PROVIDED', 'INCOMPLETE', 'INVALID')
            and input_tokens is null and output_tokens is null and total_tokens is null)
        or (token_usage_status = 'COMPLETE' and input_tokens >= 0 and output_tokens >= 0
            and total_tokens = input_tokens + output_tokens)
        or (token_usage_status is null and input_tokens is null and output_tokens is null and total_tokens is null)
    ),
    add constraint chk_ai_runtime_evidence_provider_status check (
        provider_status is null or provider_status in ('NOT_SENT', 'SENT_UNKNOWN', 'ACKNOWLEDGED', 'COMPLETED', 'FAILED')
    ),
    add constraint chk_ai_runtime_evidence_error_category check (
        error_category is null or error_category in (
            'NONE', 'CONNECTION_CONFIGURATION', 'TRANSPORT', 'PROVIDER_CLIENT_ERROR',
            'PROVIDER_SERVER_ERROR', 'RESPONSE_PARSE_ERROR'
        )
    );
