package com.adp.gateway.digitalasset.application;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.adp.gateway.digitalasset.domain.DigitalAssetActiveArtifact;
import com.adp.gateway.digitalasset.domain.DigitalAssetRuntimeSnapshot;
import com.adp.gateway.egress.domain.DestinationProfile;
import com.adp.gateway.egress.domain.ExecutionPackType;
import com.adp.gateway.policy.domain.PolicyLifecycleStage;
import com.adp.gateway.policy.domain.PolicySnapshot;
import org.springframework.stereotype.Service;

@Service
public class DigitalAssetRuntimeSnapshotService {
    private final DigitalAssetRuntimeSnapshotPersistence persistence;
    private final DigitalAssetCanonicalJson canonicalJson;

    public DigitalAssetRuntimeSnapshotService(
        DigitalAssetRuntimeSnapshotPersistence persistence,
        DigitalAssetCanonicalJson canonicalJson
    ) {
        this.persistence = persistence;
        this.canonicalJson = canonicalJson;
    }

    public Optional<DigitalAssetRuntimeSnapshot> pinIfRequired(
        String executionId,
        String institutionId,
        String workloadId,
        String purposeCode,
        DestinationProfile destination,
        PolicySnapshot policy,
        OffsetDateTime selectedAt
    ) {
        if (destination.packType() != ExecutionPackType.DIGITAL_ASSET) {
            return Optional.empty();
        }
        DigitalAssetActiveArtifact active = persistence.loadActive(institutionId, workloadId, purposeCode)
            .orElseThrow(() -> rejected("DIGITAL_ASSET_ACTIVE_ARTIFACT_NOT_FOUND"));
        validate(active, destination, policy, selectedAt);

        String snapshotId = "dasnap_" + UUID.randomUUID();
        String policySnapshotId = policy.sourcePolicyEvaluationArtifactRef().artifactId()
            + ":" + policy.sourcePolicyEvaluationArtifactRef().artifactVersion();
        Map<String, Object> identity = new LinkedHashMap<>();
        identity.put("snapshotId", snapshotId);
        identity.put("executionId", executionId);
        identity.put("institutionId", institutionId);
        identity.put("workloadId", workloadId);
        identity.put("purposeCode", purposeCode);
        identity.put("artifactId", active.artifactId());
        identity.put("artifactVersion", active.artifactVersion());
        identity.put("artifactDigest", active.artifactDigest());
        identity.put("approvedPolicySnapshotId", policySnapshotId);
        identity.put("approvedPolicyVersion", policy.policyVersion());
        identity.put("approvedPolicyDigest", policy.snapshotDigest());
        identity.put("destinationProfileId", destination.destinationProfileId());
        identity.put("destinationProfileVersion", destination.profileVersion());
        identity.put("destinationProfileDigest", destination.profileDigest());
        identity.put("runtimeControlVersion", active.runtimeControlVersion());
        identity.put("runtimeControlDigest", active.runtimeControlDigest());
        identity.put("crosswalkVersion", active.crosswalkVersion());
        identity.put("crosswalkDigest", active.crosswalkDigest());
        identity.put("selectedAt", selectedAt.toString());

        DigitalAssetRuntimeSnapshot snapshot = new DigitalAssetRuntimeSnapshot(
            snapshotId, canonicalJson.digest(identity), executionId, institutionId, workloadId, purposeCode,
            active.artifactId(), active.artifactVersion(), active.artifactDigest(), policySnapshotId,
            policy.policyVersion(), policy.snapshotDigest(), destination.destinationProfileId(),
            destination.profileVersion(), destination.profileDigest(), active.runtimeControlVersion(),
            active.runtimeControlDigest(), active.crosswalkVersion(), active.crosswalkDigest(), selectedAt
        );
        persistence.save(snapshot);
        return Optional.of(snapshot);
    }

    public void verifyPinned(
        Optional<DigitalAssetRuntimeSnapshot> pinned,
        DestinationProfile destination,
        PolicySnapshot policy
    ) {
        if (destination.packType() != ExecutionPackType.DIGITAL_ASSET) {
            return;
        }
        DigitalAssetRuntimeSnapshot snapshot = pinned
            .orElseThrow(() -> rejected("DIGITAL_ASSET_RUNTIME_SNAPSHOT_INVALID"));
        if (!snapshot.destinationProfileId().equals(destination.destinationProfileId())
            || !snapshot.destinationProfileVersion().equals(destination.profileVersion())
            || !snapshot.destinationProfileDigest().equals(destination.profileDigest())
            || !snapshot.approvedPolicyVersion().equals(policy.policyVersion())
            || !snapshot.approvedPolicyDigest().equals(policy.snapshotDigest())) {
            throw rejected("DIGITAL_ASSET_RUNTIME_SNAPSHOT_INVALID");
        }
    }

    public Optional<DigitalAssetRuntimeSnapshot> find(String executionId) {
        return persistence.findByExecutionId(executionId);
    }

    private void validate(
        DigitalAssetActiveArtifact active,
        DestinationProfile destination,
        PolicySnapshot policy,
        OffsetDateTime selectedAt
    ) {
        if (!active.destinationProfileId().equals(destination.destinationProfileId())
            || !destination.isEffectiveAt(selectedAt)
            || !destination.allows(active.workloadId(), active.purposeCode(), selectedAt)
            || policy.lifecycleStage() != PolicyLifecycleStage.ACTIVE
            || policy.effectiveAt() == null || policy.effectiveAt().isAfter(selectedAt)
            || blank(active.artifactDigest()) || blank(active.runtimeControlVersion())
            || blank(active.runtimeControlDigest()) || blank(active.crosswalkVersion())
            || blank(active.crosswalkDigest()) || blank(policy.policyVersion()) || blank(policy.snapshotDigest())
            || blank(destination.profileVersion()) || blank(destination.profileDigest())) {
            throw rejected("DIGITAL_ASSET_RUNTIME_SNAPSHOT_INVALID");
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private DigitalAssetRuntimeSnapshotException rejected(String reasonCode) {
        return new DigitalAssetRuntimeSnapshotException(reasonCode);
    }
}
