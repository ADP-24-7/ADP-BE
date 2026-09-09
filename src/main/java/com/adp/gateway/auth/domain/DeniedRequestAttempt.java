package com.adp.gateway.auth.domain;

import java.time.OffsetDateTime;

public record DeniedRequestAttempt(
    String attemptId,
    String requestId,
    String traceId,
    String clientTraceIdDigest,
    String principalId,
    String institutionId,
    String workloadId,
    String purposeCode,
    String subjectRefDigest,
    String reasonCode,
    OffsetDateTime createdAt
) {
}
