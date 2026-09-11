package com.adp.gateway.ai.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;

import com.adp.gateway.auth.domain.AdpRole;
import com.adp.gateway.auth.domain.AuthPrincipal;
import com.adp.gateway.auth.domain.PrincipalType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class AiTransformGovernanceProfileServiceTests {
    private final AiTransformGovernanceProfileService service = new AiTransformGovernanceProfileService();

    @Test
    void exposesReadOnlyE2RequirementProjectionWithoutExecutingE3() throws Exception {
        var response = service.read(principal("institution_local", Set.of("customer_summary")),
            AiEvaluationRunCatalog.EXPERIMENT_02_RUN_ID);

        assertThat(response.e2ValidationStatus()).isEqualTo("E2_POLICY_REQUIREMENT_VALIDATED");
        assertThat(response.providerGovernanceStatus()).isEqualTo("PROVIDER_GOVERNANCE_BLOCKED");
        assertThat(response.externalExecutionStatus()).isEqualTo("PENDING_EXTERNAL_EXECUTION");
        assertThat(response.providerCallAuthorized()).isFalse();
        assertThat(response.fieldControls()).hasSize(20);
        assertThat(response.requirementEnforcementGaps()).extracting(item -> item.fieldName())
            .containsExactly("account.balance", "transaction.amount");
        assertThat(response.fieldControls()).filteredOn(item -> !item.currentRuntimeRequirementMatch())
            .extracting(item -> item.fieldName())
            .containsExactly("account.balance", "transaction.amount");
        assertThat(response.e2HandoffDigest()).isEqualTo(AiTransformGovernanceProfileService.E2_HANDOFF_DIGEST);
        var json = new ObjectMapper().writeValueAsString(response);
        assertThat(json).contains("\"e2_handoff_digest\"", "\"field_controls\"",
            "\"current_runtime_requirement_match\":false", "\"provider_call_authorized\":false");
    }

    @Test
    void refusesOtherRunsAndInstitutionScopes() {
        assertThatThrownBy(() -> service.read(principal("institution_local", Set.of("customer_summary")),
            AiEvaluationRunCatalog.BASELINE_RUN_ID))
            .isInstanceOfSatisfying(AiEvaluationRunMismatchException.class, exception ->
                assertThat(exception.reasonCode()).isEqualTo("AI_TRANSFORM_GOVERNANCE_PROFILE_NOT_FOUND"));
        assertThatThrownBy(() -> service.read(principal("other", Set.of("customer_summary")),
            AiEvaluationRunCatalog.EXPERIMENT_02_RUN_ID))
            .isInstanceOf(AccessDeniedException.class);
    }

    private AuthPrincipal principal(String institutionId, Set<String> workloads) {
        return new AuthPrincipal("controller", PrincipalType.USER, "Controller", institutionId, false,
            workloads, Set.of(AdpRole.PRIVILEGED_OPERATOR));
    }
}
