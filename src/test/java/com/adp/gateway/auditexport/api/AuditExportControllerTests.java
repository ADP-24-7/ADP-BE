package com.adp.gateway.auditexport.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.adp.gateway.auditexport.application.AuditExportWorkerService;
import com.adp.gateway.auditexport.application.AuditExportPersistence;
import com.adp.gateway.auditexport.domain.AuditExportWorkView;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    "adp.local-user-auth.enabled=true",
    "adp.audit-export.scheduler.enabled=false"
})
@AutoConfigureMockMvc
class AuditExportControllerTests {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AuditExportWorkerService workerService;
    @Autowired private AuditExportPersistence exportPersistence;
    @Autowired private JdbcClient jdbcClient;

    @Test
    void makerCheckerGeneratesAndDownloadsPrivacySafeCsvWithAuditedLifecycle() throws Exception {
        String marker = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String rawPrompt = "raw-sensitive-prompt-" + marker;
        String executionId = execute(marker, rawPrompt);
        String exportId = request(executionId, "CSV", "export-key-" + marker, "auditor-local", "AUDITOR")
            .path("exportId").asText();

        mockMvc.perform(get("/api/v1/audit-exports/{exportId}/download", exportId)
                .header("X-ADP-User-Id", "auditor-local")
                .header("X-ADP-User-Roles", "AUDITOR"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.reasonCode").value("AUDIT_EXPORT_NOT_READY"));

        mockMvc.perform(post("/api/v1/audit-exports/{exportId}/approval", exportId)
                .header("X-ADP-User-Id", "privileged-operator-local")
                .header("X-ADP-User-Roles", "PRIVILEGED_OPERATOR")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"action":"APPROVE","reason":"내부 감사 제출 범위 확인"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("APPROVED"));

        assertThat(workerService.processNext("test-worker")).isTrue();

        mockMvc.perform(get("/api/v1/audit-exports/{exportId}", exportId)
                .header("X-ADP-User-Id", "auditor-local")
                .header("X-ADP-User-Roles", "AUDITOR"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.job.status").value("READY"))
            .andExpect(jsonPath("$.job.rowCount").value(1))
            .andExpect(jsonPath("$.job.contentDigest").isString())
            .andExpect(jsonPath("$.events[*].action").value(org.hamcrest.Matchers.contains(
                "REQUESTED", "APPROVED", "GENERATED"
            )));

        byte[] downloaded = mockMvc.perform(get("/api/v1/audit-exports/{exportId}/download", exportId)
                .header("X-ADP-User-Id", "auditor-local")
                .header("X-ADP-User-Roles", "AUDITOR"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", org.hamcrest.Matchers.startsWith("text/csv")))
            .andExpect(header().exists("Content-Disposition"))
            .andExpect(header().exists("X-Content-SHA256"))
            .andReturn().getResponse().getContentAsByteArray();
        String csv = new String(downloaded, StandardCharsets.UTF_8);
        assertThat(csv)
            .contains(executionId, "INTERNAL-PRIVACY-SAFE-EVIDENCE", "evidence_digest")
            .doesNotContain(rawPrompt)
            .doesNotContain("customer-100")
            .doesNotContain("local-dev-api-key");
        assertThat(eventCount(exportId, "DOWNLOADED")).isEqualTo(1);

        jdbcClient.sql("update auth_principal set enabled = false where principal_id = 'auditor-local'").update();
        try {
            mockMvc.perform(get("/api/v1/audit-exports/{exportId}/download", exportId)
                    .header("X-ADP-User-Id", "auditor-local")
                    .header("X-ADP-User-Roles", "AUDITOR"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.reasonCode").value("AUDIT_EXPORT_ACCESS_REVOKED"));
            assertThat(eventCount(exportId, "DOWNLOADED")).isEqualTo(1);
        } finally {
            jdbcClient.sql("update auth_principal set enabled = true where principal_id = 'auditor-local'").update();
        }
    }

    @Test
    void idempotencyReplaysSameScopeAndRejectsDifferentScope() throws Exception {
        String marker = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String executionId = execute(marker, "approved context");
        String key = "export-idem-" + marker;
        String firstId = request(executionId, "CSV", key, "auditor-local", "AUDITOR")
            .path("exportId").asText();
        String replayedId = request(executionId, "CSV", key, "auditor-local", "AUDITOR")
            .path("exportId").asText();
        assertThat(replayedId).isEqualTo(firstId);
        assertThat(eventCount(firstId, "REQUESTED")).isEqualTo(1);

        mockMvc.perform(post("/api/v1/audit-exports")
                .header("X-ADP-User-Id", "auditor-local")
                .header("X-ADP-User-Roles", "AUDITOR")
                .contentType(MediaType.APPLICATION_JSON)
                .content(exportRequest(executionId, "PDF", key)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.reasonCode").value("AUDIT_EXPORT_IDEMPOTENCY_CONFLICT"));

        mockMvc.perform(post("/api/v1/audit-exports")
                .header("X-ADP-User-Id", "auditor-local")
                .header("X-ADP-User-Roles", "AUDITOR")
                .contentType(MediaType.APPLICATION_JSON)
                .content(exportRequest(executionId, "CSV", key, "외부 검사기관 제출")))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.reasonCode").value("AUDIT_EXPORT_IDEMPOTENCY_CONFLICT"));
    }

    @Test
    void requesterCannotSelfApproveHighRiskExport() throws Exception {
        String marker = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String exportId = request(execute(marker, "approved context"), "PDF", "self-" + marker,
            "operator-local", "PRIVILEGED_OPERATOR").path("exportId").asText();

        mockMvc.perform(post("/api/v1/audit-exports/{exportId}/approval", exportId)
                .header("X-ADP-User-Id", "operator-local")
                .header("X-ADP-User-Roles", "PRIVILEGED_OPERATOR")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"APPROVE\",\"reason\":\"self approval\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.reasonCode").value("AUDIT_EXPORT_MAKER_CHECKER_VIOLATION"));
    }

    @Test
    void generationRechecksRequesterGrantAndFailsClosedAfterRevocation() throws Exception {
        String marker = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String exportId = request(execute(marker, "approved context"), "CSV", "revoke-" + marker,
            "auditor-local", "AUDITOR").path("exportId").asText();
        mockMvc.perform(post("/api/v1/audit-exports/{exportId}/approval", exportId)
                .header("X-ADP-User-Id", "privileged-operator-local")
                .header("X-ADP-User-Roles", "PRIVILEGED_OPERATOR")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"APPROVE\",\"reason\":\"approved before generation\"}"))
            .andExpect(status().isOk());

        jdbcClient.sql("update auth_principal set enabled = false where principal_id = 'auditor-local'").update();
        try {
            assertThat(workerService.processNext("revocation-worker")).isTrue();
            assertThat(jdbcClient.sql("select status from audit_export_job where export_id = :exportId")
                .param("exportId", exportId).query(String.class).single()).isEqualTo("REVOKED");
            assertThat(jdbcClient.sql("select content from audit_export_job where export_id = :exportId")
                .param("exportId", exportId).query(byte[].class).optional()).isEmpty();
        } finally {
            jdbcClient.sql("update auth_principal set enabled = true where principal_id = 'auditor-local'").update();
        }
    }

    @Test
    void ordinaryOperatorCannotRequestEvidenceExport() throws Exception {
        String marker = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String executionId = execute(marker, "approved context");
        mockMvc.perform(post("/api/v1/audit-exports")
                .header("X-ADP-User-Id", "ordinary-operator")
                .header("X-ADP-User-Roles", "OPERATOR")
                .contentType(MediaType.APPLICATION_JSON)
                .content(exportRequest(executionId, "CSV", "forbidden-" + marker)))
            .andExpect(status().isForbidden());
    }

    @Test
    void exposesScopedMyWorkAndApprovalQueueWithoutSelfApprovalTasks() throws Exception {
        String marker = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String executionId = execute(marker, "approved context");
        String auditorExport = request(executionId, "CSV", "work-auditor-" + marker,
            "auditor-local", "AUDITOR").path("exportId").asText();
        String approverOwnedExport = request(executionId, "PDF", "work-approver-" + marker,
            "privileged-operator-local", "PRIVILEGED_OPERATOR").path("exportId").asText();

        mockMvc.perform(get("/api/v1/audit-exports")
                .param("view", "MY_REQUESTS")
                .header("X-ADP-User-Id", "auditor-local")
                .header("X-ADP-User-Roles", "AUDITOR"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[*].exportId").value(org.hamcrest.Matchers.hasItem(auditorExport)))
            .andExpect(jsonPath("$.items[*].exportId", org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.hasItem(approverOwnedExport))));

        mockMvc.perform(get("/api/v1/audit-exports")
                .param("view", "APPROVAL_QUEUE")
                .header("X-ADP-User-Id", "privileged-operator-local")
                .header("X-ADP-User-Roles", "PRIVILEGED_OPERATOR"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[*].exportId").value(org.hamcrest.Matchers.hasItem(auditorExport)))
            .andExpect(jsonPath("$.items[*].exportId", org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.hasItem(approverOwnedExport))));

        mockMvc.perform(get("/api/v1/audit-exports/work-summary")
                .header("X-ADP-User-Id", "privileged-operator-local")
                .header("X-ADP-User-Roles", "PRIVILEGED_OPERATOR"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.principalId").value("privileged-operator-local"))
            .andExpect(jsonPath("$.approvalAvailable").value(true))
            .andExpect(jsonPath("$.personal.pendingApproval").isNumber())
            .andExpect(jsonPath("$.approvals.pending").isNumber())
            .andExpect(jsonPath("$.operations.pendingApproval").isNumber());

        mockMvc.perform(get("/api/v1/audit-exports")
                .param("view", "APPROVAL_QUEUE")
                .header("X-ADP-User-Id", "auditor-local")
                .header("X-ADP-User-Roles", "AUDITOR"))
            .andExpect(status().isForbidden());
    }

    @Test
    void workReadModelEnforcesInstitutionAndWorkloadScopeInSql() throws Exception {
        String marker = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String exportId = request(execute(marker, "approved context"), "CSV", "scope-" + marker,
            "auditor-local", "AUDITOR").path("exportId").asText();
        jdbcClient.sql("update audit_export_job set workload_id = 'restricted-workload' where export_id = :exportId")
            .param("exportId", exportId).update();

        assertThat(exportPersistence.searchWork(
            "institution_local", java.util.Set.of("customer_summary"), "auditor-local", false,
            AuditExportWorkView.MY_REQUESTS, null, 0, 100
        ).items()).extracting("exportId").doesNotContain(exportId);

        jdbcClient.sql("update audit_export_job set institution_id = 'other-institution' where export_id = :exportId")
            .param("exportId", exportId).update();
        assertThat(exportPersistence.searchWork(
            "institution_local", java.util.Set.of("*"), "auditor-local", false,
            AuditExportWorkView.MY_REQUESTS, null, 0, 100
        ).items()).extracting("exportId").doesNotContain(exportId);
    }

    @Test
    void serverOwnedPdfIsGeneratedAndExpiredContentIsDeleted() throws Exception {
        String marker = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String exportId = request(execute(marker, "approved context"), "PDF", "pdf-" + marker,
            "auditor-local", "AUDITOR").path("exportId").asText();
        mockMvc.perform(post("/api/v1/audit-exports/{exportId}/approval", exportId)
                .header("X-ADP-User-Id", "privileged-operator-local")
                .header("X-ADP-User-Roles", "PRIVILEGED_OPERATOR")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"APPROVE\",\"reason\":\"PDF 범위 확인\"}"))
            .andExpect(status().isOk());
        assertThat(workerService.processNext("pdf-worker")).isTrue();

        byte[] pdf = mockMvc.perform(get("/api/v1/audit-exports/{exportId}/download", exportId)
                .header("X-ADP-User-Id", "auditor-local")
                .header("X-ADP-User-Roles", "AUDITOR"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "application/pdf"))
            .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");

        jdbcClient.sql("update audit_export_job set expires_at = :expired where export_id = :exportId")
            .param("expired", OffsetDateTime.now().minusMinutes(1)).param("exportId", exportId).update();
        mockMvc.perform(get("/api/v1/audit-exports/{exportId}/download", exportId)
                .header("X-ADP-User-Id", "auditor-local")
                .header("X-ADP-User-Roles", "AUDITOR"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.reasonCode").value("AUDIT_EXPORT_EXPIRED"));
        assertThat(jdbcClient.sql("select content from audit_export_job where export_id = :exportId")
            .param("exportId", exportId).query(byte[].class).optional()).isEmpty();
        assertThat(eventCount(exportId, "EXPIRED")).isEqualTo(1);
        assertThat(eventCount(exportId, "DELETED")).isEqualTo(1);
    }

    private JsonNode request(
        String executionId, String format, String key, String userId, String roles
    ) throws Exception {
        String response = mockMvc.perform(post("/api/v1/audit-exports")
                .header("X-ADP-User-Id", userId)
                .header("X-ADP-User-Roles", roles)
                .contentType(MediaType.APPLICATION_JSON)
                .content(exportRequest(executionId, format, key)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("REQUESTED"))
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response);
    }

    private String exportRequest(String executionId, String format, String key) {
        return exportRequest(executionId, format, key, "내부 감사 증적 제출");
    }

    private String exportRequest(String executionId, String format, String key, String reason) {
        return """
            {"executionId":"%s","reportType":"EXECUTION_EVIDENCE","format":"%s",
             "reason":"%s","idempotencyKey":"%s"}
            """.formatted(executionId, format, reason, key);
    }

    private String execute(String marker, String prompt) throws Exception {
        String response = mockMvc.perform(post("/v1/runtime/executions")
                .header("X-Request-Id", "req_export_" + marker)
                .header("X-Trace-Id", "trace_export_" + marker)
                .header("X-ADP-API-Key", "local-dev-api-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"institutionId":"institution_local","approvalReference":"approval_ai_customer_support_v1",
                     "workloadId":"customer_summary","purposeCode":"CUSTOMER_SUPPORT",
                     "subjectScope":"customer:customer-100",
                     "destinationProfileId":"dest_internal_provider_project_provisional",
                     "idempotencyKey":"runtime-%s","processingContexts":["AI_USE"],
                     "input":{"prompt":"%s"}}
                    """.formatted(marker, prompt)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("executionId").asText();
    }

    private int eventCount(String exportId, String action) {
        return jdbcClient.sql("select count(*) from audit_export_event where export_id = :exportId and action = :action")
            .param("exportId", exportId).param("action", action).query(Integer.class).single();
    }
}
