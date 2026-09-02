package com.adp.gateway.runtime.api;

import java.util.ArrayList;
import java.util.List;

import com.adp.gateway.runtime.domain.RuntimeExecutionTrace;

public record RuntimeExecutionTraceEventsResponse(
    String executionId,
    String traceId,
    String status,
    List<RuntimeExecutionStageResponse> stages,
    RuntimeExecutionEvidenceResponse evidence
) {

    public static RuntimeExecutionTraceEventsResponse from(RuntimeExecutionTrace trace) {
        List<RuntimeExecutionStageResponse> stages = new ArrayList<>();
        stages.add(new RuntimeExecutionStageResponse("RECEIVED", "COMPLETED", trace.createdAt()));
        if (trace.status() != null && !"RECEIVED".equals(trace.status())) {
            stages.add(new RuntimeExecutionStageResponse("AUTHORIZATION", authorizationStatus(trace), trace.updatedAt()));
        }
        if (trace.canonicalContextDigest() != null) {
            stages.add(new RuntimeExecutionStageResponse("RETRIEVAL", "COMPLETED", trace.updatedAt()));
            stages.add(new RuntimeExecutionStageResponse("CANONICAL_CONTEXT", "COMPLETED", trace.updatedAt()));
        }
        if (trace.decisionId() != null) {
            stages.add(new RuntimeExecutionStageResponse("DECISION", "COMPLETED", trace.updatedAt()));
        }
        if (trace.approvalReuseStatus() != null) {
            stages.add(new RuntimeExecutionStageResponse(
                "POLICY_HARNESS",
                trace.approvalReuseStatus(),
                trace.updatedAt()
            ));
        }
        if ("APPLIED".equals(trace.transformStatus())) {
            stages.add(new RuntimeExecutionStageResponse("TRANSFORM", "COMPLETED", trace.updatedAt()));
        }
        if ("PASSED".equals(trace.outboundGuardStatus())) {
            stages.add(new RuntimeExecutionStageResponse("OUTBOUND_GUARD", "COMPLETED", trace.updatedAt()));
        }
        if (trace.connectorExecutionId() != null) {
            stages.add(new RuntimeExecutionStageResponse("PROVIDER_REQUEST", "COMPLETED", trace.updatedAt()));
            stages.add(new RuntimeExecutionStageResponse("CONNECTOR", connectorStatus(trace), trace.updatedAt()));
        }
        if (trace.responseGuardStatus() != null) {
            stages.add(new RuntimeExecutionStageResponse(
                "RESPONSE_GUARD",
                "PASSED".equals(trace.responseGuardStatus()) ? "COMPLETED" : trace.responseGuardStatus(),
                trace.updatedAt()
            ));
        }
        if ("FAILED".equals(trace.status())) {
            stages.add(new RuntimeExecutionStageResponse("RUNTIME_EXECUTION", "FAILED", trace.updatedAt()));
        }
        return new RuntimeExecutionTraceEventsResponse(
            trace.executionId(),
            trace.traceId(),
            trace.status(),
            List.copyOf(stages),
            RuntimeExecutionEvidenceResponse.from(trace)
        );
    }

    private static String authorizationStatus(RuntimeExecutionTrace trace) {
        if ("BLOCKED".equals(trace.status())) {
            return "DENIED";
        }
        return "COMPLETED";
    }

    private static String connectorStatus(RuntimeExecutionTrace trace) {
        if ("ACKNOWLEDGED".equals(trace.connectorStatus()) || "COMPLETED".equals(trace.connectorStatus())) {
            return "COMPLETED";
        }
        return trace.connectorStatus();
    }
}
