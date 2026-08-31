package com.adp.gateway.common.error;

import java.time.OffsetDateTime;

public record ErrorResponse(
    String errorCode,
    String reasonCode,
    String message,
    String requestId,
    String traceId,
    OffsetDateTime timestamp
) {

    public ErrorResponse(
        String reasonCode,
        String message,
        String requestId,
        String traceId,
        OffsetDateTime timestamp
    ) {
        this(reasonCode, reasonCode, message, requestId, traceId, timestamp);
    }
}
