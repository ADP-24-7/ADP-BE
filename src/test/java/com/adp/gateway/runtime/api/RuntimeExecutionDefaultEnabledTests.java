package com.adp.gateway.runtime.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
        mockMvc.perform(post("/v1/runtime/executions")
                .header("X-Request-Id", "req_v1_default")
                .header("X-Trace-Id", "trace_v1_default")
                .header("X-ADP-API-Key", "local-dev-api-key")
                .contentType("application/json")
                .content("""
                    {
                      "workloadId": "customer_summary",
                      "purposeCode": "CUSTOMER_SUPPORT",
                      "subjectScope": "customer:customer-100",
                      "providerProfileId": "internal-provider",
                      "idempotencyKey": "idem_v1_default",
                      "processingContexts": ["AI_USE"],
                      "input": {
                        "ticketId": "ticket-100"
                      }
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.policyVersion").value("be-runtime-policy/unconfigured/0.0.0"))
            .andExpect(jsonPath("$.finalAction").value("REVIEW"))
            .andExpect(jsonPath("$.connectorStatus").value("NOT_EXECUTED"));
    }
}
