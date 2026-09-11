package com.adp.gateway.runtime.api;

import java.util.ArrayList;
import java.util.List;
import java.time.OffsetDateTime;

import com.adp.gateway.runtime.domain.RuntimeExecutionTrace;

public record RuntimeExecutionTraceEventsResponse(
    String executionId,
    String traceId,
    String status,
    String workloadId,
    String purposeCode,
    String subjectRefDigest,
    String authorizationDecision,
    String authorizationReason,
    String policyVersion,
    String policyDecision,
    List<String> policyReasonCodes,
    String finalAction,
    List<String> regulatoryRequirementRefs,
    List<String> regulatoryEvidenceRefs,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    List<RuntimeExecutionStageResponse> stages,
    List<RuntimeStageTimingRecorder.StageTiming> stageTimings,
    DigitalAssetRuntimeSnapshotResponse digitalAssetRuntimeSnapshot,
    DigitalAssetPreExecutionGuardResponse digitalAssetPreExecutionGuard,
    DigitalAssetPostExecutionEvidenceResponse digitalAssetPostExecutionEvidence,
    RuntimeExecutionEvidenceResponse evidence
) {

    public static RuntimeExecutionTraceEventsResponse from(
        RuntimeExecutionTrace trace,
        com.adp.gateway.digitalasset.domain.DigitalAssetRuntimeSnapshot snapshot,
        com.adp.gateway.digitalasset.domain.DigitalAssetPreExecutionGuardResult preExecutionGuard,
        com.adp.gateway.digitalasset.domain.DigitalAssetPostExecutionEvidence postExecutionEvidence
    ) {
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
        if ("APPLIED".equals(trace.transformStatus())) {
            stages.add(new RuntimeExecutionStageResponse("TRANSFORM", "COMPLETED", trace.updatedAt()));
        }
        if (trace.approvalReuseStatus() != null) {
            stages.add(new RuntimeExecutionStageResponse(
                "POLICY_HARNESS",
                trace.approvalReuseStatus(),
                trace.updatedAt()
            ));
        }
        if ("PASSED".equals(trace.outboundGuardStatus())) {
            stages.add(new RuntimeExecutionStageResponse("OUTBOUND_GUARD", "COMPLETED", trace.updatedAt()));
        }
        if (preExecutionGuard != null) {
            stages.add(new RuntimeExecutionStageResponse(
                "PRE_EXECUTION_GUARD",
                "PASSED".equals(preExecutionGuard.status()) ? "COMPLETED" : preExecutionGuard.status(),
                preExecutionGuard.evaluatedAt()
            ));
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
        if (postExecutionEvidence != null) {
            stages.add(new RuntimeExecutionStageResponse(
                "POST_EXECUTION_REBINDING",
                "VERIFIED".equals(postExecutionEvidence.status().name())
                    ? "COMPLETED" : postExecutionEvidence.status().name(),
                postExecutionEvidence.observedAt()
            ));
        }
        if (trace.controlledDeliveryStatus() != null) {
            stages.add(new RuntimeExecutionStageResponse(
                "CONTROLLED_DELIVERY",
                trace.controlledDeliveryStatus(),
                trace.controlledDeliveredAt() == null ? trace.updatedAt() : trace.controlledDeliveredAt()
            ));
        }
        if ("FAILED".equals(trace.status())) {
            stages.add(new RuntimeExecutionStageResponse("RUNTIME_EXECUTION", "FAILED", trace.updatedAt()));
        }
        return new RuntimeExecutionTraceEventsResponse(
            trace.executionId(),
            trace.traceId(),
            trace.status(),
            trace.workloadId(),
            trace.purposeCode(),
            trace.subjectRefDigest(),
            "PASSED".equals(trace.authorizationStatus()) ? "ALLOWED" : "DENIED",
            "PASSED".equals(trace.authorizationStatus()) ? "AUTHORIZATION_POLICY_ALLOWED" : "AUTHORIZATION_POLICY_DENIED",
            trace.policyVersion(),
            trace.policyAction(),
            values(trace.policyReasonCodes()),
            trace.finalAction(),
            values(trace.policyRequirementRefs()),
            values(trace.policyEvidenceRefs()),
            trace.createdAt(),
            trace.updatedAt(),
            List.copyOf(stages),
            RuntimeStageTimingRecorder.snapshot(trace.executionId()),
            DigitalAssetRuntimeSnapshotResponse.from(snapshot),
            DigitalAssetPreExecutionGuardResponse.from(preExecutionGuard),
            DigitalAssetPostExecutionEvidenceResponse.from(postExecutionEvidence),
            RuntimeExecutionEvidenceResponse.from(trace)
        );
    }

    private static List<String> values(String value) {
        return value == null || value.isBlank() ? List.of()
            : java.util.Arrays.stream(value.split(",")).filter(item -> !item.isBlank()).toList();
    }

    private static String authorizationStatus(RuntimeExecutionTrace trace) {
        return "PASSED".equals(trace.authorizationStatus()) ? "COMPLETED" : trace.authorizationStatus();
    }

    private static String connectorStatus(RuntimeExecutionTrace trace) {
        if ("ACKNOWLEDGED".equals(trace.connectorStatus()) || "COMPLETED".equals(trace.connectorStatus())) {
            return "COMPLETED";
        }
        return trace.connectorStatus();
    }
}
