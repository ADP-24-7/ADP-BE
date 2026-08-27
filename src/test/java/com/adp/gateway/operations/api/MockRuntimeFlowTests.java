package com.adp.gateway.operations.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class MockRuntimeFlowTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void mockRequestCreatesTraceDecisionConnectorAndAudit() throws Exception {
        mockMvc.perform(post("/api/runtime/mock")
                .header("X-Request-Id", "req_be0_test")
                .header("X-Trace-Id", "trace_be0_test")
                .header("Idempotency-Key", "idem_be0_test")
                .contentType("application/json")
                .content("""
                    {
                      "workloadId": "workload_be0",
                      "purpose": "BE-0 local E2E",
                      "subject": "mock-subject"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(header().string("X-Request-Id", "req_be0_test"))
            .andExpect(header().string("X-Trace-Id", "trace_be0_test"))
            .andExpect(jsonPath("$.requestId").value("req_be0_test"))
            .andExpect(jsonPath("$.traceId").value("trace_be0_test"))
            .andExpect(jsonPath("$.idempotencyKey").value("idem_be0_test"))
            .andExpect(jsonPath("$.policyArtifactId").value("PROJECT_PROVISIONAL"))
            .andExpect(jsonPath("$.decisionId").exists())
            .andExpect(jsonPath("$.outcome").value("ALLOW"))
            .andExpect(jsonPath("$.reasonCode").value("MOCK_DECISION_ALLOW"))
            .andExpect(jsonPath("$.connectorStatus").value("EXECUTED"))
            .andExpect(jsonPath("$.auditId").exists());

        Integer auditCount = jdbcClient.sql("""
                select count(*) from audit_event
                where request_id = :requestId and trace_id = :traceId
                """)
            .param("requestId", "req_be0_test")
            .param("traceId", "trace_be0_test")
            .query(Integer.class)
            .single();

        assertThat(auditCount).isGreaterThanOrEqualTo(1);
    }
}
