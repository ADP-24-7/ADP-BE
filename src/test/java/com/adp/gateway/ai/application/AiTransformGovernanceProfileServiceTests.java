package com.adp.gateway.ai.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import com.adp.gateway.auth.domain.AdpRole;
import com.adp.gateway.auth.domain.AuthPrincipal;
import com.adp.gateway.auth.domain.PrincipalType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.adp.gateway.ai.domain.AiProviderGovernanceContract;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class AiTransformGovernanceProfileServiceTests {
    private final AiTransformGovernanceProfileService service = new AiTransformGovernanceProfileService(contracts());

    private static AiEvaluationContractService contracts() {
        var value = mock(AiEvaluationContractService.class);
        when(value.providerGovernanceContract()).thenReturn(new AiProviderGovernanceContract(
            "e2-provider-governance/1.2.0", "governance-digest", "ACTIVE_FAIL_CLOSED",
            "nvidia-nim-hosted", List.of("model"), "customer_summary", "CUSTOMER_SUPPORT",
            List.of("destination"), List.of("destination-digest"), List.of("KR"), true,
            "SESSION_ONLY", 0, List.of("REQUEST_EXECUTION")
        ));
        return value;
    }

    @Test
    void exposesActivatedE3RequirementProjectionWithoutExecutingProvider() throws Exception {
        var response = service.read(principal("institution_local", Set.of("customer_summary")),
            AiEvaluationRunCatalog.EXPERIMENT_02_RUN_ID);

        assertThat(response.e2ValidationStatus()).isEqualTo("E2_POLICY_REQUIREMENT_VALIDATED");
        assertThat(response.providerGovernanceStatus()).isEqualTo("PROVIDER_GOVERNANCE_BLOCKED");
        assertThat(response.externalExecutionStatus()).isEqualTo("PENDING_EXTERNAL_EXECUTION");
        assertThat(response.providerCallAuthorized()).isFalse();
        assertThat(response.fieldControls()).hasSize(20);
        assertThat(response.requirementEnforcementGaps()).isEmpty();
        assertThat(response.fieldControls()).allMatch(item -> item.currentRuntimeRequirementMatch());
        assertThat(response.e2HandoffDigest()).isEqualTo(AiTransformGovernanceProfileService.E2_HANDOFF_DIGEST);
        assertThat(response.e3ProfileDigest()).isEqualTo(AiTransformGovernanceProfileService.E3_PROFILE_DIGEST);
        assertThat(response.e3ValidationStatus()).isEqualTo("VALIDATED");
        assertThat(response.activationStatus()).isEqualTo("ACTIVATED");
        assertThat(response.providerGovernance().governanceDecision()).isEqualTo("BLOCK");
        assertThat(response.providerGovernance().reasonCodes()).containsExactly(
            "PROVIDER_REGION_REQUIRED", "RETENTION_UNVERIFIED", "MODEL_TRAINING_NOT_ALLOWED");
        var json = new ObjectMapper().writeValueAsString(response);
        assertThat(json).contains("\"e2_handoff_digest\"", "\"field_controls\"",
            "\"current_runtime_requirement_match\":true", "\"provider_call_authorized\":false",
            "\"privacy_result\":\"PASS\"", "\"activation_status\":\"ACTIVATED\"");
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
