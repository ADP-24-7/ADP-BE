package com.adp.gateway.ai.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import com.adp.gateway.ai.application.AiEvaluationRunCatalog;
import com.adp.gateway.ai.application.AiTransformGovernanceProfileService;
import com.adp.gateway.ai.application.AiEvaluationContractService;
import com.adp.gateway.ai.domain.AiProviderGovernanceContract;
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
        var contracts = mock(AiEvaluationContractService.class);
        when(contracts.providerGovernanceContract()).thenReturn(new AiProviderGovernanceContract(
            "e2-provider-governance/1.2.0", "governance-digest", "ACTIVE_FAIL_CLOSED",
            "nvidia-nim-hosted", List.of("model"), "customer_summary", "CUSTOMER_SUPPORT",
            List.of("destination"), List.of("destination-digest"), List.of("KR"), true,
            "SESSION_ONLY", 0, List.of("REQUEST_EXECUTION")
        ));
        mockMvc = MockMvcBuilders.standaloneSetup(
            new AiTransformGovernanceProfileController(new AiTransformGovernanceProfileService(contracts))
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
            .andExpect(jsonPath("$.e3_profile_version").value("1.2.0"))
            .andExpect(jsonPath("$.e3_profile_digest").value(AiTransformGovernanceProfileService.E3_PROFILE_DIGEST))
            .andExpect(jsonPath("$.e3_validation_status").value("VALIDATED"))
            .andExpect(jsonPath("$.activation_status").value("ACTIVATED"))
            .andExpect(jsonPath("$.e2_validation_status").value("E2_POLICY_REQUIREMENT_VALIDATED"))
            .andExpect(jsonPath("$.provider_governance_status").value("PROVIDER_GOVERNANCE_BLOCKED"))
            .andExpect(jsonPath("$.external_execution_status").value("PENDING_EXTERNAL_EXECUTION"))
            .andExpect(jsonPath("$.provider_call_authorized").value(false))
            .andExpect(jsonPath("$.provider_governance.governance_decision").value("BLOCK"))
            .andExpect(jsonPath("$.provider_governance.region_reason_code").value("PROVIDER_REGION_REQUIRED"))
            .andExpect(jsonPath("$.provider_governance.retention_reason_code").value("RETENTION_UNVERIFIED"))
            .andExpect(jsonPath("$.provider_governance.reuse_reason_code").value("MODEL_TRAINING_NOT_ALLOWED"))
            .andExpect(jsonPath("$.field_controls.length()").value(20))
            .andExpect(jsonPath("$.requirement_enforcement_gaps.length()").value(0))
            .andExpect(jsonPath("$.field_controls[?(@.field_name == 'transaction.amount')].field_requirement")
                .value("REQUIRED_EXACT"))
            .andExpect(jsonPath("$.field_controls[?(@.field_name == 'transaction.amount')].current_runtime_method")
                .value("KEEP"))
            .andExpect(jsonPath("$.field_controls[?(@.field_name == 'transaction.amount')].required_transform_method")
                .value("KEEP"));
    }

    private TestingAuthenticationToken authentication(String institutionId, Set<String> workloads) {
        var principal = new AuthPrincipal("controller", PrincipalType.USER, "Controller", institutionId, false,
            workloads, Set.of(AdpRole.PRIVILEGED_OPERATOR));
        return new TestingAuthenticationToken(principal, null, "ROLE_PRIVILEGED_OPERATOR");
    }
}
