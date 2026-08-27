package com.adp.gateway.auth.domain;

import java.util.Set;

public record AuthContext(
    String principalId,
    PrincipalType principalType,
    Set<AdpRole> roles,
    Set<String> workloadIds,
    boolean subjectAuthorizationRequired
) {

    public static AuthContext from(AuthPrincipal principal) {
        return new AuthContext(
            principal.principalId(),
            principal.principalType(),
            principal.roles(),
            principal.workloadIds(),
            principal.subjectAuthorizationRequired()
        );
    }
}
