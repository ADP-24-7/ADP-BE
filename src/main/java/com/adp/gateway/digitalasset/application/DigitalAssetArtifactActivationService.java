package com.adp.gateway.digitalasset.application;

import java.time.Clock;
import java.time.OffsetDateTime;

import com.adp.gateway.auth.domain.AuthPrincipal;
import com.adp.gateway.digitalasset.domain.DigitalAssetActiveArtifact;
import com.adp.gateway.policy.application.PolicyLifecycleService;
import com.adp.gateway.policy.domain.PolicyLifecycleStage;
import com.adp.gateway.policy.domain.PolicyLifecycleTransitionReason;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DigitalAssetArtifactActivationService {
    private final DigitalAssetArtifactIngestionPersistence ingestionPersistence;
    private final DigitalAssetRuntimeSnapshotPersistence snapshotPersistence;
    private final PolicyLifecycleService lifecycleService;
    private final Clock clock;

    public DigitalAssetArtifactActivationService(
        DigitalAssetArtifactIngestionPersistence ingestionPersistence,
        DigitalAssetRuntimeSnapshotPersistence snapshotPersistence,
        PolicyLifecycleService lifecycleService,
        Clock clock
    ) {
        this.ingestionPersistence = ingestionPersistence;
        this.snapshotPersistence = snapshotPersistence;
        this.lifecycleService = lifecycleService;
        this.clock = clock;
    }

    @Transactional
    public DigitalAssetActiveArtifact activate(
        AuthPrincipal principal,
        String artifactId,
        String artifactVersion
    ) {
        var ingestion = ingestionPersistence.find(
            principal.institutionId(), principal.workloadIds(), artifactId, artifactVersion
        ).orElseThrow(() -> rejected("DIGITAL_ASSET_ARTIFACT_NOT_FOUND"));
        var lifecycle = lifecycleService.load(principal, artifactId, artifactVersion);
        if (lifecycle.lifecycleStage() != PolicyLifecycleStage.APPROVED
            || !lifecycle.artifactDigest().equals(ingestion.artifactDigest())
            || !lifecycle.workloadId().equals(ingestion.workloadId())
            || !lifecycle.purposeCode().equals(ingestion.purposeCode())
            || missingRuntimeMetadata(ingestion.runtimeControlVersion(), ingestion.runtimeControlDigest(),
                ingestion.crosswalkVersion(), ingestion.crosswalkDigest())) {
            throw rejected("DIGITAL_ASSET_ACTIVE_ARTIFACT_INVALID");
        }

        lifecycleService.transition(
            principal, artifactId, artifactVersion, PolicyLifecycleStage.ACTIVE,
            PolicyLifecycleTransitionReason.ACTIVATION_APPROVED
        );
        DigitalAssetActiveArtifact active = new DigitalAssetActiveArtifact(
            ingestion.institutionId(), ingestion.workloadId(), ingestion.purposeCode(),
            ingestion.artifactId(), ingestion.artifactVersion(), ingestion.artifactDigest(),
            ingestion.destinationProfileId(), ingestion.runtimeControlVersion(), ingestion.runtimeControlDigest(),
            ingestion.crosswalkVersion(), ingestion.crosswalkDigest(), principal.principalId(),
            OffsetDateTime.now(clock)
        );
        snapshotPersistence.activate(active);
        return active;
    }

    private boolean missingRuntimeMetadata(String... values) {
        for (String value : values) {
            if (value == null || value.isBlank()) {
                return true;
            }
        }
        return false;
    }

    private DigitalAssetRuntimeSnapshotException rejected(String reasonCode) {
        return new DigitalAssetRuntimeSnapshotException(reasonCode);
    }
}
