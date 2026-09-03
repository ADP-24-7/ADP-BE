package com.adp.gateway.audit.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
    "adp.local-fixtures.enabled=true",
    "adp.mock-runtime.enabled=true",
    "adp.local-user-auth.enabled=true"
})
@AutoConfigureMockMvc
class AuditReadControllerTests {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void operatorSearchesInstitutionScopedRuntimeReadModel() throws Exception {
        String executionId = execute("search");

        mockMvc.perform(get("/api/admin/audit/executions")
                .header("X-ADP-User-Id", "operator-local")
                .header("X-ADP-User-Roles", "OPERATOR")
                .param("workloadId", "customer_summary")
                .param("status", "COMPLETED")
                .param("size", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].executionId").value(executionId))
            .andExpect(jsonPath("$.items[0].institutionId").value("institution_local"))
            .andExpect(jsonPath("$.items[0].status").value("COMPLETED"))
            .andExpect(jsonPath("$.totalElements").isNumber());
    }

    @Test
    void privilegedOperatorExportsDigestOnlyEvidencePack() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String idempotencyKey = "idem_evidence_" + suffix;
        String prompt = "sensitive-prompt-" + suffix;
        String executionId = execute(suffix, idempotencyKey, prompt);

        String response = mockMvc.perform(get("/api/admin/audit/executions/{executionId}/evidence", executionId)
                .header("X-ADP-User-Id", "privileged-local")
                .header("X-ADP-User-Roles", "PRIVILEGED_OPERATOR"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.schemaVersion").value("adp-execution-evidence/v1"))
            .andExpect(jsonPath("$.evidenceDigest").isString())
            .andExpect(jsonPath("$.evidenceDigest").value(org.hamcrest.Matchers.hasLength(64)))
            .andExpect(jsonPath("$.policy.snapshotDigest").isString())
            .andExpect(jsonPath("$.data.inputDigest").isString())
            .andExpect(jsonPath("$.egress.providerRequestDigest").isString())
            .andReturn().getResponse().getContentAsString();

        assertThat(response)
            .doesNotContain(prompt)
            .doesNotContain(idempotencyKey)
            .doesNotContain("customer-100");
    }

    @Test
    void operatorCannotExportEvidence() throws Exception {
        String executionId = execute("forbidden");

        mockMvc.perform(get("/api/admin/audit/executions/{executionId}/evidence", executionId)
                .header("X-ADP-User-Id", "operator-local")
                .header("X-ADP-User-Roles", "OPERATOR"))
            .andExpect(status().isForbidden());
    }

    private String execute(String label) throws Exception {
        String suffix = label + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return execute(suffix, "idem_audit_" + suffix, "Summarize approved context");
    }

    private String execute(String suffix, String idempotencyKey, String prompt) throws Exception {
        String response = mockMvc.perform(post("/v1/runtime/executions")
                .header("X-Request-Id", "req_audit_" + suffix)
                .header("X-Trace-Id", "trace_audit_" + suffix)
                .header("X-ADP-API-Key", "local-dev-api-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "institutionId":"institution_local",
                      "approvalReference":"approval_ai_customer_support_v1",
                      "workloadId":"customer_summary",
                      "purposeCode":"CUSTOMER_SUPPORT",
                      "subjectScope":"customer:customer-100",
                      "destinationProfileId":"dest_internal_provider_project_provisional",
                      "idempotencyKey":"%s",
                      "processingContexts":["AI_USE"],
                      "input":{"prompt":"%s"}
                    }
                    """.formatted(idempotencyKey, prompt)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return response.replaceAll(".*\\\"executionId\\\":\\\"([^\\\"]+)\\\".*", "$1");
    }
}
