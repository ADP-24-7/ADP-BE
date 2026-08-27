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
    "adp.data-access-preview.enabled=false"
})
@AutoConfigureMockMvc
class DataAccessPreviewDisabledTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void dataAccessPreviewEndpointIsDisabledByDefault() throws Exception {
        mockMvc.perform(post("/api/runtime/data-access/preview")
                .header("X-Request-Id", "req_data_access_disabled")
                .header("X-Trace-Id", "trace_data_access_disabled")
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
