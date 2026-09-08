package com.adp.gateway.digitalasset.application;

import java.time.Clock;
import java.time.OffsetDateTime;

import com.adp.gateway.auth.domain.AuthPrincipal;
import com.adp.gateway.auth.domain.AdpRole;
import com.adp.gateway.digitalasset.domain.DigitalAssetActiveArtifact;
import com.adp.gateway.policy.application.PolicyLifecycleService;
import com.adp.gateway.policy.application.PolicyLifecycleException;
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
        if (principal == null || !principal.hasRole(AdpRole.PRIVILEGED_OPERATOR)
            || principal.institutionId() == null || principal.institutionId().isBlank()) {
            throw new PolicyLifecycleException("POLICY_LIFECYCLE_FORBIDDEN");
        }
        var ingestion = ingestionPersistence.find(
            principal.institutionId(), principal.workloadIds(), artifactId, artifactVersion
        ).orElseThrow(() -> rejected("DIGITAL_ASSET_ARTIFACT_NOT_FOUND"));
        snapshotPersistence.lockActiveScope(ingestion.institutionId(), ingestion.workloadId());
        var currentActive = snapshotPersistence.loadActive(
            ingestion.institutionId(), ingestion.workloadId()
        );
        var lifecycle = lifecycleService.load(principal, artifactId, artifactVersion);
        if (currentActive.isPresent()
            && currentActive.get().artifactId().equals(artifactId)
            && currentActive.get().artifactVersion().equals(artifactVersion)) {
            if (lifecycle.lifecycleStage() != PolicyLifecycleStage.ACTIVE
                || !currentActive.get().artifactDigest().equals(ingestion.artifactDigest())) {
                throw rejected("DIGITAL_ASSET_ACTIVE_ARTIFACT_INVALID");
            }
            return currentActive.get();
        }
        if (lifecycle.lifecycleStage() != PolicyLifecycleStage.APPROVED
            || !lifecycle.artifactDigest().equals(ingestion.artifactDigest())
            || !lifecycle.workloadId().equals(ingestion.workloadId())
            || !lifecycle.purposeCode().equals(ingestion.purposeCode())
            || missingRuntimeMetadata(ingestion.runtimeControlVersion(), ingestion.runtimeControlDigest(),
                ingestion.crosswalkVersion(), ingestion.crosswalkDigest())) {
            throw rejected("DIGITAL_ASSET_ACTIVE_ARTIFACT_INVALID");
        }

        if (currentActive.isPresent()) {
            DigitalAssetActiveArtifact previous = currentActive.get();
            var previousLifecycle = lifecycleService.load(
                principal, previous.artifactId(), previous.artifactVersion()
            );
            if (previousLifecycle.lifecycleStage() != PolicyLifecycleStage.ACTIVE
                || !previousLifecycle.artifactDigest().equals(previous.artifactDigest())
                || !previousLifecycle.workloadId().equals(previous.workloadId())) {
                throw rejected("DIGITAL_ASSET_ACTIVE_ARTIFACT_INVALID");
            }
            lifecycleService.transitionForRuntimeSelection(
                principal, previous.artifactId(), previous.artifactVersion(),
                PolicyLifecycleStage.SUPERSEDED,
                PolicyLifecycleTransitionReason.ACTIVE_VERSION_SUPERSEDED
            );
        }
        lifecycleService.transitionForRuntimeSelection(
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
        snapshotPersistence.replaceActive(active);
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
