package com.adp.gateway.workload.domain;

public record WorkloadDefinition(
    String workloadId,
    String displayName,
    String description,
    boolean enabled
) {
}
