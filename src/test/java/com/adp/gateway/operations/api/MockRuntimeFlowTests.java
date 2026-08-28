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

@SpringBootTest(properties = {
    "adp.local-fixtures.enabled=true",
    "adp.mock-runtime.enabled=true"
})
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
                .header("X-ADP-API-Key", "local-dev-api-key")
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
            .andExpect(jsonPath("$.policyArtifactStatus").value("PROJECT_PROVISIONAL"))
            .andExpect(jsonPath("$.policyVersion").value("0.0.0"))
            .andExpect(jsonPath("$.policyDigest").value("local-fixture"))
            .andExpect(jsonPath("$.decisionId").exists())
            .andExpect(jsonPath("$.policyAction").value("ALLOW"))
            .andExpect(jsonPath("$.finalAction").value("REVIEW"))
            .andExpect(jsonPath("$.authorizationResult").value("ALLOWED"))
            .andExpect(jsonPath("$.applicabilityResult").value("INCOMPLETE"))
            .andExpect(jsonPath("$.runtimeContextDigest").exists())
            .andExpect(jsonPath("$.matchedRuleIds").value("PROJECT_PROVISIONAL_RULE"))
            .andExpect(jsonPath("$.evidenceRefs").value(""))
            .andExpect(jsonPath("$.requiredControls").value("RUNTIME_AUTHORIZATION,SUBJECT_SCOPE"))
            .andExpect(jsonPath("$.outcome").value("REVIEW"))
            .andExpect(jsonPath("$.reasonCode").value("POLICY_INCOMPLETE"))
            .andExpect(jsonPath("$.connectorStatus").value("NOT_EXECUTED"))
            .andExpect(jsonPath("$.auditId").exists());

        Integer auditCount = jdbcClient.sql("""
                select count(*) from audit_event
                where request_id = :requestId and trace_id = :traceId
                  and policy_artifact_id = 'PROJECT_PROVISIONAL'
                  and policy_action = 'ALLOW'
                  and final_action = 'REVIEW'
                  and authorization_result = 'ALLOWED'
                  and applicability_result = 'INCOMPLETE'
                  and runtime_context_digest is not null
                  and matched_rule_ids = 'PROJECT_PROVISIONAL_RULE'
                  and evidence_refs = ''
                  and required_controls = 'RUNTIME_AUTHORIZATION,SUBJECT_SCOPE'
                  and policy_version = '0.0.0'
                  and policy_digest = 'local-fixture'
                  and connector_status = 'NOT_EXECUTED'
                """)
            .param("requestId", "req_be0_test")
            .param("traceId", "trace_be0_test")
            .query(Integer.class)
            .single();

        assertThat(auditCount).isGreaterThanOrEqualTo(1);
    }

    @Test
    void malformedJsonUsesCommonErrorResponse() throws Exception {
        mockMvc.perform(post("/api/runtime/mock")
                .header("X-Request-Id", "req_bad_json")
                .header("X-Trace-Id", "trace_bad_json")
                .header("X-ADP-API-Key", "local-dev-api-key")
                .contentType("application/json")
                .content("{"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.reasonCode").value("MALFORMED_REQUEST"))
            .andExpect(jsonPath("$.message").value("Malformed request"))
            .andExpect(jsonPath("$.requestId").value("req_bad_json"))
            .andExpect(jsonPath("$.traceId").value("trace_bad_json"))
            .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void invalidTraceHeaderUsesCommonErrorResponseBeforeAudit() throws Exception {
        mockMvc.perform(post("/api/runtime/mock")
                .header("X-Request-Id", "x".repeat(81))
                .header("X-Trace-Id", "trace_bad_header")
                .header("X-ADP-API-Key", "local-dev-api-key")
                .contentType("application/json")
                .content("""
                    {
                      "workloadId": "workload_be0",
                      "purpose": "BE-0 local E2E",
                      "subject": "mock-subject"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.reasonCode").value("MALFORMED_REQUEST"))
            .andExpect(jsonPath("$.message").value("Malformed request"))
            .andExpect(jsonPath("$.requestId").doesNotExist())
            .andExpect(jsonPath("$.traceId").value("trace_bad_header"))
            .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void runtimeExecutionRejectsSubjectWithoutGrant() throws Exception {
        mockMvc.perform(post("/api/runtime/mock")
                .header("X-Request-Id", "req_forbidden_subject")
                .header("X-Trace-Id", "trace_forbidden_subject")
                .header("X-ADP-API-Key", "local-dev-api-key")
                .contentType("application/json")
                .content("""
                    {
                      "workloadId": "workload_be0",
                      "purpose": "BE-0 local E2E",
                      "subject": "customer:not-granted"
                    }
                    """))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.reasonCode").value("AUTHORIZATION_DENIED"))
            .andExpect(jsonPath("$.message").value("Authorization denied"))
            .andExpect(jsonPath("$.requestId").value("req_forbidden_subject"))
            .andExpect(jsonPath("$.traceId").value("trace_forbidden_subject"));
    }

    @Test
    void runtimeExecutionRejectsPurposeWithoutGrant() throws Exception {
        mockMvc.perform(post("/api/runtime/mock")
                .header("X-Request-Id", "req_forbidden_purpose")
                .header("X-Trace-Id", "trace_forbidden_purpose")
                .header("X-ADP-API-Key", "local-dev-api-key")
                .contentType("application/json")
                .content("""
                    {
                      "workloadId": "workload_be0",
                      "purpose": "MODEL_TRAINING",
                      "subject": "customer:mock-subject"
                    }
                    """))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.reasonCode").value("AUTHORIZATION_DENIED"))
            .andExpect(jsonPath("$.message").value("Authorization denied"))
            .andExpect(jsonPath("$.requestId").value("req_forbidden_purpose"))
            .andExpect(jsonPath("$.traceId").value("trace_forbidden_purpose"));
    }
}
