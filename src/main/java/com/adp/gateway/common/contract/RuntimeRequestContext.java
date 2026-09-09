package com.adp.gateway.common.contract;

public record RuntimeRequestContext(
    String requestId,
    String traceId,
    String idempotencyKey,
    String workloadId,
    String purpose,
    String subject,
    String clientTraceIdDigest
) {

    public RuntimeRequestContext(
        String requestId,
        String traceId,
        String idempotencyKey,
        String workloadId,
        String purpose,
        String subject
    ) {
        this(requestId, traceId, idempotencyKey, workloadId, purpose, subject, null);
    }
}
