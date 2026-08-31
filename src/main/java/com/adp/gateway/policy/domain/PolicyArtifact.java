package com.adp.gateway.policy.domain;

import java.util.List;

public record PolicyArtifact(
    String artifactId,
    String artifactVersion,
    PolicyLifecycleStage status,
    String workloadId,
    List<String> limitations,
    String digest
) {

    public PolicyArtifact {
        limitations = List.copyOf(limitations);
    }
}
