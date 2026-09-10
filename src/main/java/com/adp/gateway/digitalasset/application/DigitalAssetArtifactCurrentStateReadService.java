package com.adp.gateway.digitalasset.application;

import com.adp.gateway.auth.domain.AdpRole;
import com.adp.gateway.auth.domain.AuthPrincipal;
import com.adp.gateway.digitalasset.domain.DigitalAssetArtifactCurrentStateDetail;
import com.adp.gateway.digitalasset.domain.DigitalAssetArtifactCurrentStatePage;
import com.adp.gateway.policy.domain.PolicyLifecycleStage;
import org.springframework.stereotype.Service;

@Service
public class DigitalAssetArtifactCurrentStateReadService {
    private final DigitalAssetArtifactCurrentStateReadPort port;

    public DigitalAssetArtifactCurrentStateReadService(DigitalAssetArtifactCurrentStateReadPort port) {
        this.port = port;
    }

    public DigitalAssetArtifactCurrentStatePage search(
        AuthPrincipal principal,
        PolicyLifecycleStage lifecycleStage,
        String workloadId,
        String query,
        boolean currentOnly,
        int page,
        int size
    ) {
        requireReader(principal);
        String normalizedWorkload = normalize(workloadId);
        if (normalizedWorkload != null && !principal.canAccessWorkload(normalizedWorkload)) {
            throw rejected("DIGITAL_ASSET_ARTIFACT_INGEST_FORBIDDEN");
        }
        return port.search(
            principal.institutionId(), principal.workloadIds(), lifecycleStage,
            normalizedWorkload, normalize(query), currentOnly, page, size
        );
    }

    public DigitalAssetArtifactCurrentStateDetail load(
        AuthPrincipal principal,
        String artifactId,
        String artifactVersion
    ) {
        requireReader(principal);
        return port.load(
            principal.institutionId(), principal.workloadIds(), artifactId, artifactVersion
        );
    }

    private void requireReader(AuthPrincipal principal) {
        if (principal == null || principal.institutionId() == null || principal.institutionId().isBlank()
            || (!principal.hasRole(AdpRole.OPERATOR)
                && !principal.hasRole(AdpRole.PRIVILEGED_OPERATOR)
                && !principal.hasRole(AdpRole.AUDITOR))) {
            throw rejected("DIGITAL_ASSET_ARTIFACT_INGEST_FORBIDDEN");
        }
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private DigitalAssetArtifactIngestionException rejected(String reasonCode) {
        return new DigitalAssetArtifactIngestionException(reasonCode);
    }
}
