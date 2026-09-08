package com.adp.gateway.digitalasset.domain;

import java.time.OffsetDateTime;

import com.adp.gateway.policy.domain.PolicyLifecycleStage;

public record DigitalAssetArtifactIngestion(
    String institutionId,
    String artifactId,
    String artifactVersion,
    String artifactDigest,
    String manifestSchemaVersion,
    String manifestReference,
    String canonicalContractVersion,
    String canonicalContractDigest,
    String workloadId,
    String purposeCode,
    String destinationProfileId,
    int fileCount,
    PolicyLifecycleStage lifecycleStage,
    String ingestedBy,
    OffsetDateTime ingestedAt
) {
}
