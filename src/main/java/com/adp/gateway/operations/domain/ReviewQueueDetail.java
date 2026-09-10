package com.adp.gateway.operations.domain;

import java.time.OffsetDateTime;
import java.util.List;

import com.adp.gateway.egress.domain.ExecutionPackType;

public record ReviewQueueDetail(
    String executionId,
    String requestId,
    String traceId,
    String institutionId,
    ExecutionPackType executionPack,
    String workloadId,
    String purposeCode,
    String runtimeStatus,
    String finalAction,
    ReviewSource reviewSource,
    List<ReviewSource> reviewSources,
    ReviewNextAction nextAction,
    List<ReviewNextAction> nextActions,
    List<String> reasonCodes,
    String policyProfileId,
    String policyProfileVersion,
    String policyProfileDigest,
    String connectorStatus,
    String responseGuardStatus,
    String controlledDeliveryStatus,
    String recoveryId,
    String recoveryStatus,
    String retryDisposition,
    String recoveryErrorCode,
    String postExecutionEvidenceStatus,
    List<String> mismatchedFields,
    String tracePath,
    String evidencePath,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
    public ReviewQueueDetail {
        reviewSources = List.copyOf(reviewSources);
        nextActions = List.copyOf(nextActions);
        reasonCodes = List.copyOf(reasonCodes);
        mismatchedFields = List.copyOf(mismatchedFields);
    }
}
