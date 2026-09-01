package com.adp.gateway.runtime.api;

import java.time.OffsetDateTime;

import com.adp.gateway.runtime.domain.RuntimeExecutionTrace;

public record RuntimeExecutionTraceResponse(
    String executionId,
    String requestId,
    String traceId,
    String idempotencyKey,
    String workloadId,
    String purposeCode,
    String subjectRefDigest,
    String providerProfileId,
    String inputDigest,
    String canonicalContextDigest,
    String runtimeContextDigest,
    String policyVersion,
    String snapshotDigest,
    String decisionId,
    String finalAction,
    String transformExecutionId,
    String transformStatus,
    String transformOutputDigest,
    String status,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {

    public static RuntimeExecutionTraceResponse from(RuntimeExecutionTrace trace) {
        return new RuntimeExecutionTraceResponse(
            trace.executionId(),
            trace.requestId(),
            trace.traceId(),
            trace.idempotencyKey(),
            trace.workloadId(),
            trace.purposeCode(),
            trace.subjectRefDigest(),
            trace.providerProfileId(),
            trace.inputDigest(),
            trace.canonicalContextDigest(),
            trace.runtimeContextDigest(),
            trace.policyVersion(),
            trace.snapshotDigest(),
            trace.decisionId(),
            trace.finalAction(),
            trace.transformExecutionId(),
            trace.transformStatus(),
            trace.transformOutputDigest(),
            trace.status(),
            trace.createdAt(),
            trace.updatedAt()
        );
    }
}
