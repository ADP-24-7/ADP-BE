package com.adp.gateway.digitalasset.domain;

import java.time.OffsetDateTime;

public record DigitalAssetActiveArtifact(
    String institutionId,
    String workloadId,
    String purposeCode,
    String artifactId,
    String artifactVersion,
    String artifactDigest,
    String destinationProfileId,
    String runtimeControlVersion,
    String runtimeControlDigest,
    String crosswalkVersion,
    String crosswalkDigest,
    String activatedBy,
    OffsetDateTime activatedAt
) {
}
