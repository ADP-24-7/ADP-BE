package com.adp.gateway.policy.domain;

import java.util.Objects;

public record SourcePolicyEvaluationArtifactRef(
    String artifactId,
    String artifactVersion,
    ArtifactDigest artifactDigest
) {

    public SourcePolicyEvaluationArtifactRef {
        Objects.requireNonNull(artifactId, "artifactId must not be null");
        Objects.requireNonNull(artifactVersion, "artifactVersion must not be null");
        Objects.requireNonNull(artifactDigest, "artifactDigest must not be null");
    }
}
