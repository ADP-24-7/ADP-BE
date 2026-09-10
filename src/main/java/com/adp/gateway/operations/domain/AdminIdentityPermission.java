package com.adp.gateway.operations.domain;

public record AdminIdentityPermission(
    String workloadId,
    String workloadName,
    boolean workloadEnabled,
    String actionName,
    String purpose,
    String subjectType,
    int subjectGrantCount
) {
}
