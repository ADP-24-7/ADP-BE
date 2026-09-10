package com.adp.gateway.auth.api;

import java.util.Set;

import com.adp.gateway.auth.domain.AdpRole;
import com.adp.gateway.auth.domain.AuthPrincipal;

public record AuthContextResponse(
    String principalId,
    String principalType,
    String displayName,
    String institutionId,
    Set<String> roles,
    Set<String> workloadIds,
    boolean subjectAuthorizationRequired
) {

    public static AuthContextResponse from(AuthPrincipal principal) {
        return new AuthContextResponse(
            principal.principalId(),
            principal.principalType().name(),
            principal.displayName(),
            principal.institutionId(),
            principal.roles().stream().map(AdpRole::name).collect(java.util.stream.Collectors.toSet()),
            principal.workloadIds(),
            principal.subjectAuthorizationRequired()
        );
    }
}
