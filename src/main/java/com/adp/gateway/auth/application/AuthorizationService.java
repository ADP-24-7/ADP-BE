package com.adp.gateway.auth.application;

import com.adp.gateway.auth.domain.AdpRole;
import org.springframework.stereotype.Service;

@Service
public class AuthorizationService {

    private final SubjectAuthorizationPort subjectAuthorizationPort;

    public AuthorizationService(SubjectAuthorizationPort subjectAuthorizationPort) {
        this.subjectAuthorizationPort = subjectAuthorizationPort;
    }

    public AuthorizationDecision authorize(AuthorizationRequest request) {
        if (!request.principal().hasRole(requiredRole(request))) {
            return AuthorizationDecision.deny();
        }
        if (!request.principal().canAccessWorkload(request.workloadId())) {
            return AuthorizationDecision.deny();
        }
        if (request.purpose() == null || request.purpose().isBlank()) {
            return AuthorizationDecision.deny();
        }
        if (!subjectAuthorizationPort.canUsePurpose(
            request.principal().principalId(),
            request.workloadId(),
            request.action(),
            request.purpose()
        )) {
            return AuthorizationDecision.deny();
        }
        if (!request.principal().subjectAuthorizationRequired()) {
            return AuthorizationDecision.allow();
        }
        if (request.subject() == null) {
            return AuthorizationDecision.deny();
        }

        boolean allowed = subjectAuthorizationPort.canAccess(
            request.principal().principalId(),
            request.workloadId(),
            request.action(),
            request.purpose(),
            request.subject()
        );
        return allowed ? AuthorizationDecision.allow() : AuthorizationDecision.deny();
    }

    private AdpRole requiredRole(AuthorizationRequest request) {
        return switch (request.action()) {
            case RUNTIME_EXECUTE -> AdpRole.RUNTIME_EXECUTOR;
            case POLICY_VIEW -> AdpRole.AUDITOR;
            case POLICY_ACTIVATE, POLICY_ROLLBACK, VAULT_REMAP, EVIDENCE_EXPORT -> AdpRole.PRIVILEGED_OPERATOR;
        };
    }
}
