package com.adp.gateway.digitalasset.application;

import java.util.Set;

import com.adp.gateway.digitalasset.domain.DigitalAssetArtifactCurrentStateDetail;
import com.adp.gateway.digitalasset.domain.DigitalAssetArtifactCurrentStatePage;
import com.adp.gateway.policy.domain.PolicyLifecycleStage;

public interface DigitalAssetArtifactCurrentStateReadPort {
    DigitalAssetArtifactCurrentStatePage search(
        String institutionId,
        Set<String> allowedWorkloads,
        PolicyLifecycleStage lifecycleStage,
        String workloadId,
        String query,
        boolean currentOnly,
        int page,
        int size
    );

    DigitalAssetArtifactCurrentStateDetail load(
        String institutionId,
        Set<String> allowedWorkloads,
        String artifactId,
        String artifactVersion
    );
}
