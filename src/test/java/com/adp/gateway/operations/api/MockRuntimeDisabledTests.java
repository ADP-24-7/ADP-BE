package com.adp.gateway.operations.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "adp.mock-runtime.enabled=false")
@AutoConfigureMockMvc
class MockRuntimeDisabledTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void mockRuntimeEndpointIsDisabledByDefault() throws Exception {
        mockMvc.perform(post("/api/runtime/mock")
                .header("X-Request-Id", "req_mock_disabled")
                .header("X-Trace-Id", "trace_mock_disabled")
                .header("X-ADP-API-Key", "local-dev-api-key")
                .contentType("application/json")
                .content("""
                    {
                      "workloadId": "workload_be0",
                      "purpose": "disabled-check",
                      "subject": "mock-subject"
                    }
                    """))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.reasonCode").value("MALFORMED_REQUEST"))
            .andExpect(jsonPath("$.requestId").value("req_mock_disabled"))
            .andExpect(jsonPath("$.traceId").value("trace_mock_disabled"));
    }
}
