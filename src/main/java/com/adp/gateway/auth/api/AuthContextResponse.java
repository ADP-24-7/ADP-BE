package com.adp.gateway.auth.api;

import java.util.Set;

public record AuthContextResponse(
    String principalId,
    String principalType,
    Set<String> roles,
    Set<String> workloadIds,
    boolean subjectAuthorizationRequired
) {
}
