package com.adp.gateway.operations.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

import com.adp.gateway.egress.domain.ExecutionPackType;
import com.adp.gateway.operations.application.SecurityFindingNotFoundException;
import com.adp.gateway.operations.application.SecurityFindingReadPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
    "adp.local-fixtures.enabled=true",
    "adp.mock-runtime.enabled=true",
    "adp.local-user-auth.enabled=true"
})
@AutoConfigureMockMvc
class SecurityFindingControllerTests {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private SecurityFindingReadPort readPort;

    @Test
    void operatorFindsPrivacySafeResponseFindingAndOpensTraceLinks() throws Exception {
        FindingFixture fixture = createFinding();

        mockMvc.perform(get("/api/admin/security-findings")
                .header("X-ADP-User-Id", "operator-local")
                .header("X-ADP-User-Roles", "OPERATOR")
                .param("executionPack", "AI")
                .param("workloadId", "customer_summary")
                .param("findingType", "PHONE_NUMBER")
                .param("size", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].findingId").value(fixture.findingId()))
            .andExpect(jsonPath("$.items[0].executionId").value(fixture.executionId()))
            .andExpect(jsonPath("$.items[0].executionPack").value("AI"))
            .andExpect(jsonPath("$.items[0].findingType").value("PHONE_NUMBER"))
            .andExpect(jsonPath("$.items[0].location").value("$.choices[0].message.content"))
            .andExpect(jsonPath("$.items[0].evidenceDigest").value("a".repeat(64)))
            .andExpect(jsonPath("$.items[0].rawValue").doesNotExist())
            .andExpect(jsonPath("$.totalElements").isNumber());

        mockMvc.perform(get("/api/admin/security-findings/{findingId}", fixture.findingId())
                .header("X-ADP-User-Id", "auditor-local")
                .header("X-ADP-User-Roles", "AUDITOR"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.executionId").value(fixture.executionId()))
            .andExpect(jsonPath("$.startOffset").value(5))
            .andExpect(jsonPath("$.endOffset").value(18))
            .andExpect(jsonPath("$.responseGuardStatus").value("PASSED"))
            .andExpect(jsonPath("$.tracePath")
                .value("/v1/runtime/executions/" + fixture.executionId() + "/trace"))
            .andExpect(jsonPath("$.evidencePath")
                .value("/api/admin/audit/executions/" + fixture.executionId() + "/evidence"))
            .andExpect(jsonPath("$.rawValue").doesNotExist());
    }

    @Test
    void readPortAlwaysAppliesInstitutionAndWorkloadScope() throws Exception {
        FindingFixture fixture = createFinding();

        assertThat(readPort.search(
            "institution_local", Set.of("fraud_detection"), ExecutionPackType.AI,
            null, null, null, null, 0, 10
        ).items()).noneMatch(item -> item.findingId() == fixture.findingId());
        assertThat(readPort.search(
            "institution_other", Set.of("*"), ExecutionPackType.AI,
            null, null, null, null, 0, 10
        ).items()).noneMatch(item -> item.findingId() == fixture.findingId());
        assertThatThrownBy(() -> readPort.load(
            fixture.findingId(), "institution_local", Set.of("fraud_detection")
        )).isInstanceOf(SecurityFindingNotFoundException.class);
    }

    @Test
    void nonOperationsRoleCannotReadSecurityFindings() throws Exception {
        mockMvc.perform(get("/api/admin/security-findings")
                .header("X-ADP-User-Id", "developer-local")
                .header("X-ADP-User-Roles", "DEVELOPER"))
            .andExpect(status().isForbidden());
    }

    @Test
    void invalidTimeRangeIsRejectedAsMalformedRequest() throws Exception {
        mockMvc.perform(get("/api/admin/security-findings")
                .header("X-ADP-User-Id", "operator-local")
                .header("X-ADP-User-Roles", "OPERATOR")
                .param("from", "2026-09-10T01:00:00Z")
                .param("to", "2026-09-10T00:00:00Z"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("MALFORMED_REQUEST"));
    }

    private FindingFixture createFinding() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String response = mockMvc.perform(post("/v1/runtime/executions")
                .header("X-Request-Id", "req_security_finding_" + suffix)
                .header("X-Trace-Id", "trace_security_finding_" + suffix)
                .header("X-ADP-API-Key", "local-dev-api-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "institutionId":"institution_local",
                      "approvalReference":"approval_ai_customer_support_v1",
                      "workloadId":"customer_summary",
                      "purposeCode":"CUSTOMER_SUPPORT",
                      "subjectScope":"customer:customer-100",
                      "destinationProfileId":"dest_internal_provider_project_provisional",
                      "idempotencyKey":"idem_security_finding_%s",
                      "processingContexts":["AI_USE"],
                      "input":{"prompt":"Summarize the approved customer context"}
                    }
                    """.formatted(suffix)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        String executionId = response.replaceAll(".*\\\"executionId\\\":\\\"([^\\\"]+)\\\".*", "$1");
        String connectorExecutionId = jdbcClient.sql("""
                select connector_execution_id
                from runtime.response_guard_result
                where execution_id = :executionId
                """)
            .param("executionId", executionId)
            .query(String.class)
            .single();
        long findingId = jdbcClient.sql("""
                insert into runtime.response_sensitive_finding (
                    connector_execution_id, execution_id, finding_type, location,
                    start_offset, end_offset, detector_version, evidence_digest, created_at
                ) values (
                    :connectorExecutionId, :executionId, 'PHONE_NUMBER', '$.choices[0].message.content',
                    5, 18, 'ai-response-regex-v2', :evidenceDigest, :createdAt
                ) returning id
                """)
            .param("connectorExecutionId", connectorExecutionId)
            .param("executionId", executionId)
            .param("evidenceDigest", "a".repeat(64))
            .param("createdAt", OffsetDateTime.now())
            .query(Long.class)
            .single();
        return new FindingFixture(findingId, executionId);
    }

    private record FindingFixture(long findingId, String executionId) { }
}
