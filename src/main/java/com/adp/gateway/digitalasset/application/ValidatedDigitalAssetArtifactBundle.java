package com.adp.gateway.digitalasset.application;

import java.util.List;

import com.adp.gateway.digitalasset.domain.DigitalAssetArtifactFileRole;

public record ValidatedDigitalAssetArtifactBundle(
    String artifactId,
    String artifactVersion,
    String artifactDigest,
    String manifestSchemaVersion,
    String manifestReference,
    String institutionId,
    String workloadId,
    String purposeCode,
    String destinationProfileId,
    String canonicalContractVersion,
    String canonicalContractDigest,
    List<ArtifactFile> files
) {
    public ValidatedDigitalAssetArtifactBundle {
        files = List.copyOf(files);
    }

    public record ArtifactFile(
        DigitalAssetArtifactFileRole role,
        String artifactVersion,
        String reference,
        String digest,
        String schemaReference,
        String schemaDigest
    ) {
    }
}
