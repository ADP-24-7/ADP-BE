package com.adp.gateway.digitalasset.domain;

import java.time.OffsetDateTime;

public record DigitalAssetRuntimeSnapshot(
    String snapshotId,
    String snapshotDigest,
    String executionId,
    String institutionId,
    String workloadId,
    String purposeCode,
    String artifactId,
    String artifactVersion,
    String artifactDigest,
    String approvedPolicySnapshotId,
    String approvedPolicyVersion,
    String approvedPolicyDigest,
    String destinationProfileId,
    String destinationProfileVersion,
    String destinationProfileDigest,
    String runtimeControlVersion,
    String runtimeControlDigest,
    String crosswalkVersion,
    String crosswalkDigest,
    OffsetDateTime selectedAt
) {
}
