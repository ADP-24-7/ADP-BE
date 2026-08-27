package com.adp.gateway.common.error;

import java.time.OffsetDateTime;

public record ErrorResponse(
    String reasonCode,
    String message,
    String requestId,
    String traceId,
    OffsetDateTime timestamp
) {
}
