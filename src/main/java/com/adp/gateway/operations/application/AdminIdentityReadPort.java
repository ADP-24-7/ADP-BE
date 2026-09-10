package com.adp.gateway.operations.application;

import java.util.Set;

import com.adp.gateway.auth.domain.AdpRole;
import com.adp.gateway.auth.domain.PrincipalType;
import com.adp.gateway.operations.domain.AdminIdentityDetail;
import com.adp.gateway.operations.domain.AdminIdentityPage;

public interface AdminIdentityReadPort {
    AdminIdentityPage search(
        String institutionId,
        Set<String> allowedWorkloads,
        PrincipalType principalType,
        AdpRole role,
        String workloadId,
        Boolean enabled,
        String query,
        int page,
        int size
    );

    AdminIdentityDetail load(String principalId, String institutionId, Set<String> allowedWorkloads);
}
