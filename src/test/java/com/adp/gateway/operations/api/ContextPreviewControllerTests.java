package com.adp.gateway.operations.api;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
    "adp.local-fixtures.enabled=true",
    "adp.context-preview.enabled=true"
})
@AutoConfigureMockMvc
class ContextPreviewControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void buildsCanonicalContextAndDetectionResultFromRetrieval() throws Exception {
        mockMvc.perform(post("/api/runtime/context/preview")
                .header("X-Request-Id", "req_context_ok")
                .header("X-Trace-Id", "trace_context_ok")
                .header("X-ADP-API-Key", "local-dev-api-key")
                .contentType("application/json")
                .content("""
                    {
                      "workloadId": "customer_summary",
                      "purpose": "CUSTOMER_SUPPORT",
                      "subject": "customer:customer-100"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.contextId").exists())
            .andExpect(jsonPath("$.dataAccessId").exists())
            .andExpect(jsonPath("$.subjectRefDigest").isString())
            .andExpect(jsonPath("$.contextDigest").isString())
            .andExpect(jsonPath("$.fields[*].fieldName", hasItem("customer_id")))
            .andExpect(jsonPath("$.fields[*].fieldName", hasItem("balance")))
            .andExpect(jsonPath("$.fields[*].fieldName", not(hasItem("account_number"))))
            .andExpect(jsonPath("$.fields[*].value").doesNotExist())
            .andExpect(jsonPath("$.fields[*].valueDigest").isArray())
            .andExpect(jsonPath("$.detection.detectorVersion").value("regex-dev-1"))
            .andExpect(jsonPath("$.detection.findings", hasSize(0)));
    }
}
