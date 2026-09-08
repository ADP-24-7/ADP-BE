package com.adp.gateway.runtime.api;

import java.time.OffsetDateTime;

import com.adp.gateway.digitalasset.domain.DigitalAssetRuntimeSnapshot;

public record DigitalAssetRuntimeSnapshotResponse(
    String snapshotId,
    String snapshotDigest,
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
    static DigitalAssetRuntimeSnapshotResponse from(DigitalAssetRuntimeSnapshot value) {
        if (value == null) {
            return null;
        }
        return new DigitalAssetRuntimeSnapshotResponse(
            value.snapshotId(), value.snapshotDigest(), value.artifactId(), value.artifactVersion(),
            value.artifactDigest(), value.approvedPolicySnapshotId(), value.approvedPolicyVersion(),
            value.approvedPolicyDigest(), value.destinationProfileId(), value.destinationProfileVersion(),
            value.destinationProfileDigest(), value.runtimeControlVersion(), value.runtimeControlDigest(),
            value.crosswalkVersion(), value.crosswalkDigest(), value.selectedAt()
        );
    }
}
