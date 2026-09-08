package com.adp.gateway.digitalasset.application;

import java.time.Clock;
import java.time.OffsetDateTime;

import com.adp.gateway.auth.domain.AdpRole;
import com.adp.gateway.auth.domain.AuthPrincipal;
import com.adp.gateway.digitalasset.domain.DigitalAssetArtifactIngestion;
import com.adp.gateway.digitalasset.domain.DigitalAssetArtifactFileRole;
import com.adp.gateway.egress.domain.ExecutionPackType;
import com.adp.gateway.policy.application.PolicyLifecycleException;
import com.adp.gateway.policy.application.PolicyLifecycleService;
import com.adp.gateway.policy.domain.PolicyLayer;
import com.adp.gateway.policy.domain.PolicyLifecycleStage;
import com.adp.gateway.policy.domain.PolicyLifecycleTransitionReason;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DigitalAssetArtifactLoaderService {
    private final DigitalAssetArtifactContentStore store;
    private final DigitalAssetArtifactBundleValidator validator;
    private final DigitalAssetArtifactIngestionPersistence persistence;
    private final PolicyLifecycleService lifecycleService;
    private final Clock clock;

    public DigitalAssetArtifactLoaderService(
        DigitalAssetArtifactContentStore store,
        DigitalAssetArtifactBundleValidator validator,
        DigitalAssetArtifactIngestionPersistence persistence,
        PolicyLifecycleService lifecycleService,
        Clock clock
    ) {
        this.store = store;
        this.validator = validator;
        this.persistence = persistence;
        this.lifecycleService = lifecycleService;
        this.clock = clock;
    }

    @Transactional
    public DigitalAssetArtifactIngestion ingest(
        AuthPrincipal principal,
        String manifestReference,
        String expectedContentDigest
    ) {
        requireOperator(principal);
        ValidatedDigitalAssetArtifactBundle bundle = validator.validate(
            manifestReference, expectedContentDigest, store
        );
        requireBinding(principal, bundle);

        persistence.lockIdentity(
            principal.institutionId(), bundle.artifactId(), bundle.artifactVersion()
        );
        var existing = persistence.find(
            principal.institutionId(), principal.workloadIds(), bundle.artifactId(), bundle.artifactVersion()
        );
        var runtimeControl = file(bundle, DigitalAssetArtifactFileRole.OUTBOUND_REQUIREMENT_MATRIX);
        var crosswalk = file(bundle, DigitalAssetArtifactFileRole.RUNTIME_DATA_CROSSWALK);
        if (existing.isPresent()) {
            DigitalAssetArtifactIngestion value = existing.get();
            if (value.artifactDigest().equals(bundle.artifactDigest())
                && value.manifestReference().equals(bundle.manifestReference())) {
                persistence.updateRuntimeMetadata(
                    value.institutionId(), value.artifactId(), value.artifactVersion(),
                    runtimeControl.artifactVersion(), runtimeControl.digest(),
                    crosswalk.artifactVersion(), crosswalk.digest()
                );
                return persistence.find(
                    principal.institutionId(), principal.workloadIds(), bundle.artifactId(), bundle.artifactVersion()
                ).orElseThrow(() -> rejected("DIGITAL_ASSET_ARTIFACT_NOT_FOUND"));
            }
            throw rejected("DIGITAL_ASSET_ARTIFACT_CONFLICT");
        }

        try {
            lifecycleService.create(
                principal, bundle.artifactId(), bundle.artifactVersion(), bundle.artifactDigest(),
                PolicyLayer.WORKLOAD, ExecutionPackType.DIGITAL_ASSET, bundle.workloadId(), bundle.purposeCode()
            );
            lifecycleService.transition(
                principal, bundle.artifactId(), bundle.artifactVersion(), PolicyLifecycleStage.VALIDATED,
                PolicyLifecycleTransitionReason.VALIDATION_PASSED
            );
            lifecycleService.transition(
                principal, bundle.artifactId(), bundle.artifactVersion(), PolicyLifecycleStage.CANDIDATE,
                PolicyLifecycleTransitionReason.CANDIDATE_PROMOTED
            );
        } catch (PolicyLifecycleException exception) {
            throw new DigitalAssetArtifactIngestionException("DIGITAL_ASSET_ARTIFACT_CONFLICT", exception);
        }

        return persistence.create(new DigitalAssetArtifactIngestion(
            principal.institutionId(), bundle.artifactId(), bundle.artifactVersion(), bundle.artifactDigest(),
            bundle.manifestSchemaVersion(), bundle.manifestReference(), bundle.canonicalContractVersion(),
            bundle.canonicalContractDigest(), bundle.workloadId(), bundle.purposeCode(),
            bundle.destinationProfileId(), runtimeControl.artifactVersion(), runtimeControl.digest(),
            crosswalk.artifactVersion(), crosswalk.digest(), bundle.files().size(), PolicyLifecycleStage.CANDIDATE,
            principal.principalId(), OffsetDateTime.now(clock)
        ));
    }

    public DigitalAssetArtifactIngestion load(
        AuthPrincipal principal,
        String artifactId,
        String artifactVersion
    ) {
        requireReader(principal);
        return persistence.find(principal.institutionId(), principal.workloadIds(), artifactId, artifactVersion)
            .orElseThrow(() -> rejected("DIGITAL_ASSET_ARTIFACT_NOT_FOUND"));
    }

    private void requireBinding(AuthPrincipal principal, ValidatedDigitalAssetArtifactBundle bundle) {
        if (!principal.institutionId().equals(bundle.institutionId())
            || !principal.canAccessWorkload(bundle.workloadId())) {
            throw rejected("DIGITAL_ASSET_ARTIFACT_BINDING_INVALID");
        }
    }

    private ValidatedDigitalAssetArtifactBundle.ArtifactFile file(
        ValidatedDigitalAssetArtifactBundle bundle,
        DigitalAssetArtifactFileRole role
    ) {
        return bundle.files().stream()
            .filter(file -> file.role() == role)
            .findFirst()
            .orElseThrow(() -> rejected("DIGITAL_ASSET_ARTIFACT_REFERENCE_INVALID"));
    }

    private void requireOperator(AuthPrincipal principal) {
        requireInstitution(principal);
        if (!principal.hasRole(AdpRole.OPERATOR)) {
            throw rejected("DIGITAL_ASSET_ARTIFACT_INGEST_FORBIDDEN");
        }
    }

    private void requireReader(AuthPrincipal principal) {
        requireInstitution(principal);
        if (!principal.hasRole(AdpRole.OPERATOR) && !principal.hasRole(AdpRole.PRIVILEGED_OPERATOR)
            && !principal.hasRole(AdpRole.AUDITOR)) {
            throw rejected("DIGITAL_ASSET_ARTIFACT_INGEST_FORBIDDEN");
        }
    }

    private void requireInstitution(AuthPrincipal principal) {
        if (principal == null || principal.institutionId() == null || principal.institutionId().isBlank()) {
            throw rejected("DIGITAL_ASSET_ARTIFACT_INGEST_FORBIDDEN");
        }
    }

    private DigitalAssetArtifactIngestionException rejected(String reasonCode) {
        return new DigitalAssetArtifactIngestionException(reasonCode);
    }
}
