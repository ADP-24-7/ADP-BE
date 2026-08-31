package com.adp.gateway.runtime.domain;

import java.time.OffsetDateTime;

public record RuntimeExecutionTrace(
    String executionId,
    String requestId,
    String traceId,
    String idempotencyKey,
    String workloadId,
    String purposeCode,
    String subjectRefDigest,
    String providerProfileId,
    String canonicalContextDigest,
    String runtimeContextDigest,
    String policyVersion,
    String snapshotDigest,
    String decisionId,
    String finalAction,
    String status,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
}
