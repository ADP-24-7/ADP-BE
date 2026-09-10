package com.adp.gateway.operations.domain;

import java.time.OffsetDateTime;

import com.adp.gateway.egress.domain.ExecutionPackType;

public record SecurityFindingItem(
    long findingId,
    String executionId,
    String requestId,
    String traceId,
    String institutionId,
    ExecutionPackType executionPack,
    String workloadId,
    String purposeCode,
    String findingType,
    String location,
    String detectorVersion,
    String evidenceDigest,
    OffsetDateTime createdAt
) { }
