package com.adp.gateway.runtime.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
    "adp.local-fixtures.enabled=true",
    "adp.mock-runtime.enabled=false"
})
@AutoConfigureMockMvc
class RuntimeExecutionDefaultEnabledTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void runtimeExecutionApiIsAvailableWhenMockRuntimeIsDisabled() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        mockMvc.perform(post("/v1/runtime/executions")
                .header("X-Request-Id", "req_def_" + suffix)
                .header("X-Trace-Id", "trace_def_" + suffix)
                .header("X-ADP-API-Key", "local-dev-api-key")
                .contentType("application/json")
                .content("""
                    {
                      "institutionId": "institution_local",
                      "approvalReference": "approval_ai_customer_support_v1",
                      "workloadId": "customer_summary",
                      "purposeCode": "CUSTOMER_SUPPORT",
                      "subjectScope": "customer:customer-100",
                      "destinationProfileId": "dest_internal_provider_project_provisional",
                      "idempotencyKey": "idem_def_%s",
                      "processingContexts": ["AI_USE"],
                      "input": {
                        "prompt": "Summarize the approved customer context"
                      }
                    }
                    """.formatted(suffix)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.policyVersion").value("be-runtime-policy/unconfigured/0.0.0"))
            .andExpect(jsonPath("$.finalAction").value("REVIEW"))
            .andExpect(jsonPath("$.connectorStatus").value("NOT_SENT"));
    }
}
