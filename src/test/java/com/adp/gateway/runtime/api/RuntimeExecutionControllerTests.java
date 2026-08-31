package com.adp.gateway.runtime.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

@SpringBootTest(properties = {
    "adp.local-fixtures.enabled=true",
    "adp.mock-runtime.enabled=true"
})
@AutoConfigureMockMvc
class RuntimeExecutionControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void createsRuntimeExecutionThroughCanonicalContextAndDecision() throws Exception {
        String response = mockMvc.perform(post("/v1/runtime/executions")
                .header("X-Request-Id", "req_v1_runtime")
                .header("X-Trace-Id", "trace_v1_runtime")
                .header("X-ADP-API-Key", "local-dev-api-key")
                .contentType("application/json")
                .content("""
                    {
                      "workloadId": "customer_summary",
                      "purposeCode": "CUSTOMER_SUPPORT",
                      "subjectScope": "customer:customer-100",
                      "providerProfileId": "internal-provider",
                      "idempotencyKey": "idem_v1_runtime",
                      "processingContexts": ["AI_USE"],
                      "input": {
                        "ticketId": "ticket-100"
                      }
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(header().string("X-Request-Id", "req_v1_runtime"))
            .andExpect(header().string("X-Trace-Id", "trace_v1_runtime"))
            .andExpect(jsonPath("$.executionId").exists())
            .andExpect(jsonPath("$.status").value("DECIDED"))
            .andExpect(jsonPath("$.policyAction").value("ALLOW"))
            .andExpect(jsonPath("$.finalAction").value("ALLOW"))
            .andExpect(jsonPath("$.authorizationResult").value("ALLOWED"))
            .andExpect(jsonPath("$.applicabilityResult").value("APPLICABLE"))
            .andExpect(jsonPath("$.runtimeContextDigest").exists())
            .andExpect(jsonPath("$.policyVersion").value("be-runtime-policy/0.0.0"))
            .andExpect(jsonPath("$.snapshotDigest").value("be-snapshot-local-fixture:customer_summary:CUSTOMER_SUPPORT:internal-provider"))
            .andExpect(jsonPath("$.sourceArtifactId").value("PROJECT_PROVISIONAL_POLICY_EVALUATION"))
            .andExpect(jsonPath("$.sourceArtifactVersion").value("0.0.0"))
            .andExpect(jsonPath("$.sourceArtifactDigestAlgorithm").value("sha256"))
            .andExpect(jsonPath("$.sourceArtifactDigestValue").value("local-fixture-policy-evaluation"))
            .andExpect(jsonPath("$.connectorStatus").value("EXECUTED"))
            .andReturn()
            .getResponse()
            .getContentAsString();

        String executionId = response.replaceAll(".*\\\"executionId\\\":\\\"([^\\\"]+)\\\".*", "$1");

        Integer executionCount = jdbcClient.sql("""
                select count(*) from runtime_execution
                where execution_id = :executionId
                  and status = 'DECIDED'
                  and provider_profile_id = 'internal-provider'
                  and canonical_context_digest is not null
                  and runtime_context_digest is not null
                  and decision_id is not null
                """)
            .param("executionId", executionId)
            .query(Integer.class)
            .single();
        assertThat(executionCount).isEqualTo(1);

        Integer decisionCount = jdbcClient.sql("""
                select count(*) from runtime_decision
                where execution_id = :executionId
                  and final_action = 'ALLOW'
                  and applicability_result = 'APPLICABLE'
                """)
            .param("executionId", executionId)
            .query(Integer.class)
            .single();
        assertThat(decisionCount).isEqualTo(1);

        mockMvc.perform(get("/v1/runtime/executions/{executionId}/trace", executionId)
                .header("X-ADP-API-Key", "local-dev-api-key"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.executionId").value(executionId))
            .andExpect(jsonPath("$.status").value("DECIDED"))
            .andExpect(jsonPath("$.canonicalContextDigest").exists())
            .andExpect(jsonPath("$.runtimeContextDigest").exists());
    }
}
