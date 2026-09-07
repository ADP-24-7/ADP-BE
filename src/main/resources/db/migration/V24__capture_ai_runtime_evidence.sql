alter table runtime.ai_model_execution_evidence
    add column measurement_type varchar(40),
    add column time_to_first_response_ms bigint,
    add column provider_latency_ms bigint,
    add column input_tokens integer,
    add column output_tokens integer,
    add column total_tokens integer,
    add column provider_status varchar(40),
    add column error_category varchar(80);

alter table runtime.ai_model_execution_evidence
    add constraint chk_ai_runtime_evidence_timing check (
        (measurement_type is null and time_to_first_response_ms is null and provider_latency_ms is null)
        or (measurement_type in ('HTTP_RESPONSE', 'MOCK')
            and time_to_first_response_ms >= 0 and provider_latency_ms >= time_to_first_response_ms)
    ),
    add constraint chk_ai_runtime_evidence_tokens check (
        (input_tokens is null and output_tokens is null and total_tokens is null)
        or (input_tokens >= 0 and output_tokens >= 0 and total_tokens = input_tokens + output_tokens)
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
