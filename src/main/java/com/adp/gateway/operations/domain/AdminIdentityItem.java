package com.adp.gateway.operations.domain;

import java.time.OffsetDateTime;
import java.util.List;

import com.adp.gateway.auth.domain.AdpRole;
import com.adp.gateway.auth.domain.PrincipalType;

public record AdminIdentityItem(
    String principalId,
    PrincipalType principalType,
    String displayName,
    String institutionId,
    boolean enabled,
    boolean subjectAuthorizationRequired,
    List<AdpRole> roles,
    List<String> workloadIds,
    int enabledApiKeyCount,
    int totalApiKeyCount,
    OffsetDateTime createdAt
) {
    public AdminIdentityItem {
        roles = List.copyOf(roles);
        workloadIds = List.copyOf(workloadIds);
    }
}
