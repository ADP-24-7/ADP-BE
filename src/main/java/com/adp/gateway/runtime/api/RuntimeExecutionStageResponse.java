package com.adp.gateway.runtime.api;

import java.time.OffsetDateTime;

public record RuntimeExecutionStageResponse(
    String stage,
    String status,
    OffsetDateTime observedAt
) {
}
