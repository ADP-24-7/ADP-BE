package com.adp.gateway.context.application;

import java.time.OffsetDateTime;

public record ExecutionPackRequestScope(
    String institutionId,
    String workloadId,
    String purpose,
    String subjectRefDigest,
    String destinationProfileId,
    OffsetDateTime requestStartedAt
) {
}
