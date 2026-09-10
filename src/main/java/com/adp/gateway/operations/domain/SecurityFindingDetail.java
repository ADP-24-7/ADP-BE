package com.adp.gateway.operations.domain;

import java.time.OffsetDateTime;

import com.adp.gateway.egress.domain.ExecutionPackType;

public record SecurityFindingDetail(
    long findingId,
    String executionId,
    String requestId,
    String traceId,
    String institutionId,
    ExecutionPackType executionPack,
    String workloadId,
    String purposeCode,
    String runtimeStatus,
    String findingType,
    String location,
    int startOffset,
    int endOffset,
    String detectorVersion,
    String evidenceDigest,
    String connectorExecutionId,
    String connectorStatus,
    String responseGuardStatus,
    String responseDigest,
    String tracePath,
    String evidencePath,
    OffsetDateTime createdAt
) { }
