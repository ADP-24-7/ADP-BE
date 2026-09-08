package com.adp.gateway.digitalasset.application;

import java.util.Optional;

import com.adp.gateway.digitalasset.domain.DigitalAssetActiveArtifact;
import com.adp.gateway.digitalasset.domain.DigitalAssetRuntimeSnapshot;
import com.adp.gateway.digitalasset.domain.DigitalAssetPreExecutionGuardResult;

public interface DigitalAssetRuntimeSnapshotPersistence {
    void lockActiveScope(String institutionId, String workloadId);

    void replaceActive(DigitalAssetActiveArtifact artifact);

    Optional<DigitalAssetActiveArtifact> loadActive(
        String institutionId,
        String workloadId
    );

    void save(DigitalAssetRuntimeSnapshot snapshot);

    Optional<DigitalAssetRuntimeSnapshot> findByExecutionId(String executionId);

    void savePreExecutionGuard(DigitalAssetPreExecutionGuardResult result);

    Optional<DigitalAssetPreExecutionGuardResult> findPreExecutionGuard(String executionId);
}
