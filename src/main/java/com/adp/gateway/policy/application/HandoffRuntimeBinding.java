package com.adp.gateway.policy.application;

public record HandoffRuntimeBinding(
    String mappingStatus,
    String runtimeDataClass,
    String workloadId,
    String purpose,
    String bindingRef
) {
}
