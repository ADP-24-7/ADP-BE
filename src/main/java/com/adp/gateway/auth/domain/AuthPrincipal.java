package com.adp.gateway.auth.domain;

import java.util.Set;

public record AuthPrincipal(
    String principalId,
    PrincipalType principalType,
    String displayName,
    String institutionId,
    boolean subjectAuthorizationRequired,
    Set<String> workloadIds,
    Set<AdpRole> roles
) {

    public boolean hasRole(AdpRole role) {
        return roles.contains(role);
    }

    public boolean canAccessWorkload(String workloadId) {
        return workloadIds.contains("*") || workloadIds.contains(workloadId);
    }
}
