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
    String destinationProfileId,
    String destinationProfileVersion,
    String destinationProfileDigest,
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
    String outboundPayloadId,
    String outboundCandidateDigest,
    String outboundGuardStatus,
    String connectorExecutionId,
    String connectorStatus,
    String responseGuardStatus,
    String status,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    RuntimeExecutionEvidenceResponse evidence
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
            trace.destinationProfileId(),
            trace.destinationProfileVersion(),
            trace.destinationProfileDigest(),
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
            trace.outboundPayloadId(),
            trace.outboundCandidateDigest(),
            trace.outboundGuardStatus(),
            trace.connectorExecutionId(),
            trace.connectorStatus(),
            trace.responseGuardStatus(),
            trace.status(),
            trace.createdAt(),
            trace.updatedAt(),
            RuntimeExecutionEvidenceResponse.from(trace)
        );
    }
}
