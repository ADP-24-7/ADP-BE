package com.adp.gateway.auth.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import com.adp.gateway.auth.domain.AdpRole;
import com.adp.gateway.auth.domain.AuthPrincipal;
import com.adp.gateway.auth.domain.PrincipalType;
import org.junit.jupiter.api.Test;

class AuthorizationServiceTests {

    private final AuthorizationService authorizationService = new AuthorizationService();

    @Test
    void allowsRuntimeExecutorInWorkloadScope() {
        AuthPrincipal principal = principal("workload-a", false, AdpRole.RUNTIME_EXECUTOR);

        boolean allowed = authorizationService.canExecuteRuntime(principal, "workload-a", null);

        assertThat(allowed).isTrue();
    }

    @Test
    void deniesRuntimeExecutionWithoutRole() {
        AuthPrincipal principal = principal("*", false, AdpRole.AUDITOR);

        boolean allowed = authorizationService.canExecuteRuntime(principal, "workload-a", "subject-a");

        assertThat(allowed).isFalse();
    }

    @Test
    void deniesRuntimeExecutionOutsideWorkloadScope() {
        AuthPrincipal principal = principal("workload-a", false, AdpRole.RUNTIME_EXECUTOR);

        boolean allowed = authorizationService.canExecuteRuntime(principal, "workload-b", "subject-b");

        assertThat(allowed).isFalse();
    }

    @Test
    void deniesMissingSubjectWhenSubjectAuthorizationIsRequired() {
        AuthPrincipal principal = principal("*", true, AdpRole.RUNTIME_EXECUTOR);

        boolean allowed = authorizationService.canExecuteRuntime(principal, "workload-a", " ");

        assertThat(allowed).isFalse();
    }

    private AuthPrincipal principal(String workloadScope, boolean subjectAuthorizationRequired, AdpRole role) {
        return new AuthPrincipal(
            "principal",
            PrincipalType.SERVICE,
            "Principal",
            workloadScope,
            subjectAuthorizationRequired,
            Set.of(role)
        );
    }
}
