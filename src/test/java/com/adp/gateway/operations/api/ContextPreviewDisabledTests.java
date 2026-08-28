package com.adp.gateway.operations.api;

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
    "adp.context-preview.enabled=false"
})
@AutoConfigureMockMvc
class ContextPreviewDisabledTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void contextPreviewEndpointIsDisabledByDefault() throws Exception {
        mockMvc.perform(post("/api/runtime/context/preview")
                .header("X-Request-Id", "req_context_disabled")
                .header("X-Trace-Id", "trace_context_disabled")
                .header("X-ADP-API-Key", "local-dev-api-key")
                .contentType("application/json")
                .content("""
                    {
                      "workloadId": "customer_summary",
                      "purpose": "CUSTOMER_SUPPORT",
                      "subject": "customer:customer-100"
                    }
                    """))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.reasonCode").value("MALFORMED_REQUEST"));
    }
}
