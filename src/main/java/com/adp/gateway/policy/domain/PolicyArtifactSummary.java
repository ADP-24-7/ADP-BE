package com.adp.gateway.policy.domain;

import java.time.OffsetDateTime;

import com.adp.gateway.egress.domain.ExecutionPackType;

public record PolicyArtifactSummary(
    String artifactId,
    String artifactVersion,
    String artifactDigest,
    PolicyLayer policyLayer,
    ExecutionPackType executionPack,
    String workloadId,
    String purposeCode,
    PolicyLifecycleStage lifecycleStage,
    String createdBy,
    long revision,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    boolean currentSelection,
    boolean actionable,
    PolicyLifecycleNextAction nextAction
) {
}
