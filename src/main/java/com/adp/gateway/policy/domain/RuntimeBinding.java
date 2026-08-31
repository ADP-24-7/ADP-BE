package com.adp.gateway.policy.domain;

import java.util.Objects;

public record RuntimeBinding(
    String mappingStatus,
    String runtimeDataClass,
    String workloadId,
    String purpose,
    String bindingRef
) {

    public RuntimeBinding {
        Objects.requireNonNull(mappingStatus, "mappingStatus must not be null");
        Objects.requireNonNull(runtimeDataClass, "runtimeDataClass must not be null");
        Objects.requireNonNull(workloadId, "workloadId must not be null");
        Objects.requireNonNull(purpose, "purpose must not be null");
    }
}
