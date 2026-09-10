package com.adp.gateway.operations.domain;

import java.time.OffsetDateTime;
import java.util.List;

import com.adp.gateway.egress.domain.ExecutionPackType;

public record ReviewQueueItem(
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
    ReviewNextAction nextAction,
    List<String> reasonCodes,
    String recoveryId,
    String recoveryStatus,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
    public ReviewQueueItem {
        reasonCodes = List.copyOf(reasonCodes);
    }
}
