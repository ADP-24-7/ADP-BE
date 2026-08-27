package com.adp.gateway.common.trace;

public final class TraceHeaders {

    public static final String REQUEST_ID = "X-Request-Id";
    public static final String TRACE_ID = "X-Trace-Id";
    public static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    public static final String REQUEST_ID_ATTRIBUTE = "adp.requestId";
    public static final String TRACE_ID_ATTRIBUTE = "adp.traceId";

    private TraceHeaders() {
    }
}
