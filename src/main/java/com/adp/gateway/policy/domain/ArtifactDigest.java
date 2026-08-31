package com.adp.gateway.policy.domain;

import java.util.Objects;

public record ArtifactDigest(
    String algorithm,
    String value
) {

    public ArtifactDigest {
        Objects.requireNonNull(algorithm, "algorithm must not be null");
        Objects.requireNonNull(value, "value must not be null");
    }
}
