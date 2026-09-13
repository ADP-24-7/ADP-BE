package com.adp.gateway.ai.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
    "adp.local-user-auth.enabled=true",
    "adp.recovery.scheduler.enabled=false"
})
@AutoConfigureMockMvc
class AiOperationsOverviewControllerTests {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbcClient;

    private String executionId;
    private String requestId;

    @BeforeEach
    void seedAiExecution() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        executionId = "exec_ai_overview_" + suffix;
        requestId = "request_ai_" + suffix;
        OffsetDateTime now = OffsetDateTime.now();
        jdbcClient.sql("""
                insert into runtime.runtime_execution (
                    execution_id, request_id, trace_id, idempotency_key, workload_id,
                    idempotency_institution_id, request_hash, purpose_code, input_digest,
                    institution_id, execution_pack, policy_version, final_action, status,
                    created_at, updated_at
                ) values (
                    :executionId, :requestId, :traceId, :idempotencyKey, 'customer_summary',
                    'institution_local', :requestHash, 'CUSTOMER_SUPPORT', :inputDigest,
                    'institution_local', 'AI', 'overview-policy-v1', 'BLOCK', 'BLOCKED',
                    :now, :now
                )
                """)
            .param("executionId", executionId)
            .param("requestId", requestId)
            .param("traceId", "trace_ai_" + suffix)
            .param("idempotencyKey", "idempotency_ai_" + suffix)
            .param("requestHash", "a".repeat(64))
            .param("inputDigest", "b".repeat(64))
            .param("now", now)
            .update();
        jdbcClient.sql("""
                insert into runtime.runtime_decision (
                    execution_id, decision_id, policy_action, final_action,
                    authorization_result, applicability_result, runtime_context_digest,
                    reason_codes, created_at
                ) values (
                    :executionId, :decisionId, 'BLOCK', 'BLOCK', 'ALLOWED', 'APPLICABLE',
                    :digest, 'APPROVAL_SCOPE_MISMATCH', :now
                )
                """)
            .param("executionId", executionId)
            .param("decisionId", "decision_ai_" + suffix)
            .param("digest", "c".repeat(64))
            .param("now", now)
            .update();
    }

    @AfterEach
    void cleanUp() {
        jdbcClient.sql("delete from runtime.runtime_decision where execution_id = :executionId")
            .param("executionId", executionId).update();
        jdbcClient.sql("delete from runtime.runtime_execution where execution_id = :executionId")
            .param("executionId", executionId).update();
    }

    @Test
    void auditorReadsScopedAiOverview() throws Exception {
        mockMvc.perform(get("/api/admin/ai/overview")
                .header("X-ADP-User-Id", "auditor-overview")
                .header("X-ADP-User-Roles", "AUDITOR"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.schemaVersion").value("adp-ai-operations-overview/v1"))
            .andExpect(jsonPath("$.metrics.total.current").isNumber())
            .andExpect(jsonPath("$.rates.policyCoverage.current").isNumber())
            .andExpect(jsonPath("$.flow[?(@.source == 'WORKLOAD_customer_summary' && @.target == 'REQUESTED')]").exists())
            .andExpect(jsonPath("$.flow[?(@.source == 'REQUESTED' && @.target == 'POLICY_BLOCK')]").exists())
            .andExpect(jsonPath("$.flow[?(@.source == 'POLICY_BLOCK' && @.target == 'DATA_NOT_REQUIRED')]").exists())
            .andExpect(jsonPath("$.flow[?(@.source == 'DATA_NOT_REQUIRED' && @.target == 'MINIMIZATION_NOT_REQUIRED')]").exists())
            .andExpect(jsonPath("$.flow[?(@.source == 'MINIMIZATION_NOT_REQUIRED' && @.target == 'PROVIDER_NOT_CALLED')]").exists())
            .andExpect(jsonPath("$.flow[?(@.source == 'PROVIDER_NOT_CALLED' && @.target == 'RESPONSE_NOT_REQUIRED')]").exists())
            .andExpect(jsonPath("$.flow[?(@.source == 'RESPONSE_NOT_REQUIRED' && @.target == 'FINAL_BLOCKED')]").exists())
            .andExpect(jsonPath("$.trend").isArray())
            .andExpect(jsonPath("$.dataClassControls").isArray())
            .andExpect(jsonPath("$.dataClassControls[0].protectionRequired").isBoolean())
            .andExpect(jsonPath("$.latency").isArray())
            .andExpect(jsonPath("$.policyCoverage").isArray())
            .andExpect(jsonPath("$.workloadViolations[?(@.workloadId == 'customer_summary' && @.violationType == 'POLICY_CONTROL')]").exists())
            .andExpect(jsonPath("$.workloadOutcomes[?(@.workloadId == 'customer_summary')]").exists())
            .andExpect(jsonPath("$.actionSummary.policyBlocked").isNumber())
            .andExpect(jsonPath("$.recentSignals").isArray())
            .andExpect(jsonPath("$.recentExecutions.items").isArray())
            .andExpect(jsonPath("$.coverage.policyDecisions").isNumber());
    }

    @Test
    void filtersAndPagesRecentAiExecutions() throws Exception {
        mockMvc.perform(get("/api/admin/ai/overview")
                .header("X-ADP-User-Id", "auditor-overview")
                .header("X-ADP-User-Roles", "AUDITOR")
                .param("query", requestId)
                .param("status", "BLOCKED")
                .param("page", "0")
                .param("size", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.recentExecutions.totalElements").value(1))
            .andExpect(jsonPath("$.recentExecutions.items[0].executionId").value(executionId));
    }

    @Test
    void rejectsRangesLongerThanThirtyOneDays() throws Exception {
        mockMvc.perform(get("/api/admin/ai/overview")
                .header("X-ADP-User-Id", "auditor-overview")
                .header("X-ADP-User-Roles", "AUDITOR")
                .param("from", "2026-01-01T00:00:00Z")
                .param("to", "2026-03-01T00:00:00Z"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsUnsupportedRecentExecutionFilters() throws Exception {
        mockMvc.perform(get("/api/admin/ai/overview")
                .header("X-ADP-User-Id", "auditor-overview")
                .header("X-ADP-User-Roles", "AUDITOR")
                .param("status", "UNKNOWN_STATUS")
                .param("size", "51"))
            .andExpect(status().isBadRequest());
    }
}
