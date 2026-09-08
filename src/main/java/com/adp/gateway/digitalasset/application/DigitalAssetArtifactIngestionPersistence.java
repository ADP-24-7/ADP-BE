package com.adp.gateway.digitalasset.application;

import java.util.Optional;
import java.util.Set;

import com.adp.gateway.digitalasset.domain.DigitalAssetArtifactIngestion;

public interface DigitalAssetArtifactIngestionPersistence {
    DigitalAssetArtifactIngestion create(DigitalAssetArtifactIngestion ingestion);

    Optional<DigitalAssetArtifactIngestion> find(
        String institutionId,
        Set<String> allowedWorkloads,
        String artifactId,
        String artifactVersion
    );
}
