package com.adp.gateway.auth.domain;

import java.util.Set;

public record AuthPrincipal(
    String principalId,
    PrincipalType principalType,
    String displayName,
    String workloadScope,
    boolean subjectAuthorizationRequired,
    Set<AdpRole> roles
) {

    public boolean hasRole(AdpRole role) {
        return roles.contains(role);
    }

    public boolean canAccessWorkload(String workloadId) {
        return "*".equals(workloadScope) || workloadScope.equals(workloadId);
    }
}
