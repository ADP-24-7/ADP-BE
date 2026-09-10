package com.adp.gateway.digitalasset.domain;

import java.time.OffsetDateTime;

import com.adp.gateway.policy.domain.PolicyLifecycleStage;

public record DigitalAssetArtifactCurrentStateItem(
    String artifactId,
    String artifactVersion,
    String artifactDigest,
    String workloadId,
    String purposeCode,
    String destinationProfileId,
    PolicyLifecycleStage lifecycleStage,
    long revision,
    DigitalAssetCurrentSelectionStatus currentSelectionStatus,
    long runtimeExecutionCount,
    String latestExecutionId,
    String latestRuntimeStatus,
    OffsetDateTime activatedAt,
    OffsetDateTime ingestedAt,
    OffsetDateTime updatedAt
) { }
