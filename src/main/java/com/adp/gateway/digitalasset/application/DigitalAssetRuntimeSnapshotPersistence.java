package com.adp.gateway.digitalasset.application;

import java.util.Optional;

import com.adp.gateway.digitalasset.domain.DigitalAssetActiveArtifact;
import com.adp.gateway.digitalasset.domain.DigitalAssetRuntimeSnapshot;

public interface DigitalAssetRuntimeSnapshotPersistence {
    void activate(DigitalAssetActiveArtifact artifact);

    Optional<DigitalAssetActiveArtifact> loadActive(
        String institutionId,
        String workloadId,
        String purposeCode
    );

    void save(DigitalAssetRuntimeSnapshot snapshot);

    Optional<DigitalAssetRuntimeSnapshot> findByExecutionId(String executionId);
}
