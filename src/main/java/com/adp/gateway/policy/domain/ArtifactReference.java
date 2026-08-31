package com.adp.gateway.policy.domain;

import java.util.Objects;

public record ArtifactReference(
    String refId,
    String refType,
    String version
) {

    public ArtifactReference {
        Objects.requireNonNull(refId, "refId must not be null");
        Objects.requireNonNull(refType, "refType must not be null");
    }

    public String auditValue() {
        return String.join(":", refId, refType, version == null ? "<none>" : version);
    }
}
