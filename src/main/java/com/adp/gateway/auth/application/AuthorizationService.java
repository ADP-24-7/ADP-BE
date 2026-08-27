package com.adp.gateway.auth.application;

import com.adp.gateway.auth.domain.AdpRole;
import com.adp.gateway.auth.domain.AuthPrincipal;
import org.springframework.stereotype.Service;

@Service
public class AuthorizationService {

    public boolean canExecuteRuntime(AuthPrincipal principal, String workloadId, String subject) {
        if (!principal.hasRole(AdpRole.RUNTIME_EXECUTOR)) {
            return false;
        }
        if (!principal.canAccessWorkload(workloadId)) {
            return false;
        }
        return !principal.subjectAuthorizationRequired() || subject != null && !subject.isBlank();
    }
}
