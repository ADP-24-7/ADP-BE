package com.adp.gateway.ai.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;

import com.adp.gateway.ai.application.AiEvaluationRunCatalog;
import com.adp.gateway.ai.application.AiTransformGovernanceProfileService;
import com.adp.gateway.auth.domain.AdpRole;
import com.adp.gateway.auth.domain.AuthPrincipal;
import com.adp.gateway.auth.domain.PrincipalType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AiTransformGovernanceProfileControllerTests {
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
            new AiTransformGovernanceProfileController(new AiTransformGovernanceProfileService())
        ).build();
    }

    @Test
    void privilegedControllerReadsImmutableFieldProfileFromAdminApi() throws Exception {
        mockMvc.perform(get("/api/admin/ai/evaluation-runs/{runId}/transform-governance-profile",
                AiEvaluationRunCatalog.EXPERIMENT_02_RUN_ID)
                .principal(authentication("institution_local", Set.of("customer_summary"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.evaluation_run_id").value(AiEvaluationRunCatalog.EXPERIMENT_02_RUN_ID))
            .andExpect(jsonPath("$.e2_handoff_digest").value(AiTransformGovernanceProfileService.E2_HANDOFF_DIGEST))
            .andExpect(jsonPath("$.requirement_version").value("1.1.1"))
            .andExpect(jsonPath("$.e2_validation_status").value("E2_POLICY_REQUIREMENT_VALIDATED"))
            .andExpect(jsonPath("$.provider_governance_status").value("PROVIDER_GOVERNANCE_BLOCKED"))
            .andExpect(jsonPath("$.external_execution_status").value("PENDING_EXTERNAL_EXECUTION"))
            .andExpect(jsonPath("$.provider_call_authorized").value(false))
            .andExpect(jsonPath("$.field_controls.length()").value(20))
            .andExpect(jsonPath("$.requirement_enforcement_gaps.length()").value(2))
            .andExpect(jsonPath("$.field_controls[?(@.field_name == 'transaction.amount')].field_requirement")
                .value("REQUIRED_EXACT"))
            .andExpect(jsonPath("$.field_controls[?(@.field_name == 'transaction.amount')].current_runtime_method")
                .value("GENERALIZE"))
            .andExpect(jsonPath("$.field_controls[?(@.field_name == 'transaction.amount')].required_transform_method")
                .value("KEEP"));
    }

    private TestingAuthenticationToken authentication(String institutionId, Set<String> workloads) {
        var principal = new AuthPrincipal("controller", PrincipalType.USER, "Controller", institutionId, false,
            workloads, Set.of(AdpRole.PRIVILEGED_OPERATOR));
        return new TestingAuthenticationToken(principal, null, "ROLE_PRIVILEGED_OPERATOR");
    }
}
