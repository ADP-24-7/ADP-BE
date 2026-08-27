package com.adp.gateway.auth.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import com.adp.gateway.auth.domain.AdpRole;
import com.adp.gateway.auth.domain.AuthPrincipal;
import com.adp.gateway.auth.domain.PrincipalType;
import com.adp.gateway.auth.domain.RuntimeAction;
import com.adp.gateway.auth.domain.SubjectRef;
import org.junit.jupiter.api.Test;

class AuthorizationServiceTests {

    @Test
    void allowsRuntimeExecutorInWorkloadScope() {
        AuthorizationService authorizationService = authorizationService(true, true);
        AuthPrincipal principal = principal(Set.of("workload-a"), false, AdpRole.RUNTIME_EXECUTOR);

        boolean allowed = authorizationService.authorize(request(principal, "workload-a", "purpose-a", null)).allowed();

        assertThat(allowed).isTrue();
    }

    @Test
    void deniesRuntimeExecutionWithoutRole() {
        AuthorizationService authorizationService = authorizationService(true, true);
        AuthPrincipal principal = principal(Set.of("*"), false, AdpRole.AUDITOR);

        boolean allowed = authorizationService.authorize(
            request(principal, "workload-a", "purpose-a", SubjectRef.from("customer:subject-a"))
        ).allowed();

        assertThat(allowed).isFalse();
    }

    @Test
    void deniesRuntimeExecutionOutsideWorkloadScope() {
        AuthorizationService authorizationService = authorizationService(true, true);
        AuthPrincipal principal = principal(Set.of("workload-a"), false, AdpRole.RUNTIME_EXECUTOR);

        boolean allowed = authorizationService.authorize(
            request(principal, "workload-b", "purpose-a", SubjectRef.from("customer:subject-b"))
        ).allowed();

        assertThat(allowed).isFalse();
    }

    @Test
    void deniesMissingSubjectWhenSubjectAuthorizationIsRequired() {
        AuthorizationService authorizationService = authorizationService(true, true);
        AuthPrincipal principal = principal(Set.of("*"), true, AdpRole.RUNTIME_EXECUTOR);

        boolean allowed = authorizationService.authorize(request(principal, "workload-a", "purpose-a", null)).allowed();

        assertThat(allowed).isFalse();
    }

    @Test
    void deniesDifferentSubjectWhenSubjectAuthorizationIsRequired() {
        AuthorizationService authorizationService = authorizationService(true, false);
        AuthPrincipal principal = principal(Set.of("workload-a"), true, AdpRole.RUNTIME_EXECUTOR);

        boolean allowed = authorizationService.authorize(
            request(principal, "workload-a", "purpose-a", SubjectRef.from("customer:subject-b"))
        ).allowed();

        assertThat(allowed).isFalse();
    }

    @Test
    void deniesPurposeWithoutGrantWhenSubjectAuthorizationIsNotRequired() {
        AuthorizationService authorizationService = authorizationService(false, true);
        AuthPrincipal principal = principal(Set.of("workload-a"), false, AdpRole.RUNTIME_EXECUTOR);

        boolean allowed = authorizationService.authorize(request(principal, "workload-a", "purpose-b", null)).allowed();

        assertThat(allowed).isFalse();
    }

    @Test
    void deniesPrivilegedActionForOperator() {
        AuthorizationService authorizationService = authorizationService(true, true);
        AuthPrincipal principal = principal(Set.of("workload-a"), false, AdpRole.OPERATOR);

        boolean allowed = authorizationService.authorize(
            request(principal, "workload-a", RuntimeAction.POLICY_ACTIVATE, "purpose-a", null)
        ).allowed();

        assertThat(allowed).isFalse();
    }

    @Test
    void allowsPrivilegedActionForPrivilegedOperator() {
        AuthorizationService authorizationService = authorizationService(true, true);
        AuthPrincipal principal = principal(Set.of("workload-a"), false, AdpRole.PRIVILEGED_OPERATOR);

        boolean allowed = authorizationService.authorize(
            request(principal, "workload-a", RuntimeAction.POLICY_ACTIVATE, "purpose-a", null)
        ).allowed();

        assertThat(allowed).isTrue();
    }

    private AuthorizationService authorizationService(boolean purposeGrantAllowed, boolean subjectGrantAllowed) {
        return new AuthorizationService(new SubjectAuthorizationPort() {
            @Override
            public boolean canAccess(
                String principalId,
                String workloadId,
                RuntimeAction action,
                String purpose,
                SubjectRef subject
            ) {
                return subjectGrantAllowed;
            }

            @Override
            public boolean canUsePurpose(String principalId, String workloadId, RuntimeAction action, String purpose) {
                return purposeGrantAllowed;
            }
        });
    }

    private com.adp.gateway.auth.application.AuthorizationRequest request(
        AuthPrincipal principal,
        String workloadId,
        String purpose,
        SubjectRef subject
    ) {
        return request(principal, workloadId, RuntimeAction.RUNTIME_EXECUTE, purpose, subject);
    }

    private com.adp.gateway.auth.application.AuthorizationRequest request(
        AuthPrincipal principal,
        String workloadId,
        RuntimeAction action,
        String purpose,
        SubjectRef subject
    ) {
        return new com.adp.gateway.auth.application.AuthorizationRequest(
            principal,
            workloadId,
            action,
            purpose,
            subject
        );
    }

    private AuthPrincipal principal(Set<String> workloadIds, boolean subjectAuthorizationRequired, AdpRole role) {
        return new AuthPrincipal(
            "principal",
            PrincipalType.SERVICE,
            "Principal",
            subjectAuthorizationRequired,
            workloadIds,
            Set.of(role)
        );
    }
}
