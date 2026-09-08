package com.adp.gateway.digitalasset.application;

import java.util.Optional;
import java.util.Set;

import com.adp.gateway.digitalasset.domain.DigitalAssetArtifactIngestion;

public interface DigitalAssetArtifactIngestionPersistence {
    void lockIdentity(String institutionId, String artifactId, String artifactVersion);

    void updateRuntimeMetadata(
        String institutionId,
        String artifactId,
        String artifactVersion,
        String runtimeControlVersion,
        String runtimeControlDigest,
        String crosswalkVersion,
        String crosswalkDigest
    );

    DigitalAssetArtifactIngestion create(DigitalAssetArtifactIngestion ingestion);

    Optional<DigitalAssetArtifactIngestion> find(
        String institutionId,
        Set<String> allowedWorkloads,
        String artifactId,
        String artifactVersion
    );
}
