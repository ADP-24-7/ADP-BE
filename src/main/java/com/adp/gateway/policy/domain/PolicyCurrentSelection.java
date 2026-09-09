package com.adp.gateway.policy.domain;

import java.time.OffsetDateTime;

import com.adp.gateway.egress.domain.ExecutionPackType;

public record PolicyCurrentSelection(
    String institutionId,
    PolicyLayer policyLayer,
    ExecutionPackType executionPack,
    String workloadId,
    String purposeCode,
    String artifactId,
    String artifactVersion,
    String artifactDigest,
    long artifactRevision,
    long selectionRevision,
    String selectedBy,
    OffsetDateTime selectedAt
) {
}
