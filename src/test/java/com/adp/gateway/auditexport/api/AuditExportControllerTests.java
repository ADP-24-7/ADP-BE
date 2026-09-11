package com.adp.gateway.auditexport.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
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
    @Autowired private PlatformTransactionManager transactionManager;

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
            "privileged-operator-local", "PRIVILEGED_OPERATOR").path("exportId").asText();

        mockMvc.perform(post("/api/v1/audit-exports/{exportId}/approval", exportId)
                .header("X-ADP-User-Id", "privileged-operator-local")
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
    void privilegedOperatorCanRevokeApprovedGeneratingAndReadyExportsWithRequiredReason() throws Exception {
        String marker = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String executionId = execute(marker, "approved context");
        String[] exportIds = {
            request(executionId, "CSV", "manual-revoke-approved-" + marker,
                "auditor-local", "AUDITOR").path("exportId").asText(),
            request(executionId, "CSV", "manual-revoke-generating-" + marker,
                "auditor-local", "AUDITOR").path("exportId").asText(),
            request(executionId, "CSV", "manual-revoke-ready-" + marker,
                "auditor-local", "AUDITOR").path("exportId").asText()
        };
        for (String exportId : exportIds) {
            mockMvc.perform(post("/api/v1/audit-exports/{exportId}/approval", exportId)
                    .header("X-ADP-User-Id", "privileged-operator-local")
                    .header("X-ADP-User-Roles", "PRIVILEGED_OPERATOR")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"action\":\"APPROVE\",\"reason\":\"승인 범위 확인\"}"))
                .andExpect(status().isOk());
        }

        mockMvc.perform(post("/api/v1/audit-exports/{exportId}/approval", exportIds[0])
                .header("X-ADP-User-Id", "privileged-operator-local")
                .header("X-ADP-User-Roles", "PRIVILEGED_OPERATOR")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"REVOKE\",\"reason\":\"\"}"))
            .andExpect(status().isBadRequest());
        revoke(exportIds[0], "승인 대상 오류");
        assertThat(eventCount(exportIds[0], "REVOKED")).isEqualTo(1);
        assertThat(eventCount(exportIds[0], "DELETED")).isZero();
        assertThat(jdbcClient.sql("""
                select approval_reason from audit_export_job where export_id = :exportId
                """).param("exportId", exportIds[0]).query(String.class).single())
            .isEqualTo("승인 범위 확인");
        assertThat(jdbcClient.sql("""
                select revocation_reason from audit_export_job where export_id = :exportId
                """).param("exportId", exportIds[0]).query(String.class).single())
            .isEqualTo("승인 대상 오류");
        assertThat(jdbcClient.sql("""
                select reason_text from audit_export_event
                where export_id = :exportId and action = 'APPROVED'
                """).param("exportId", exportIds[0]).query(String.class).single())
            .isEqualTo("승인 범위 확인");
        assertThat(jdbcClient.sql("""
                select reason_text from audit_export_event
                where export_id = :exportId and action = 'REVOKED'
                """).param("exportId", exportIds[0]).query(String.class).single())
            .isEqualTo("승인 대상 오류");

        jdbcClient.sql("""
                update audit_export_job
                set status = 'GENERATING', lease_owner = 'test-worker',
                    lease_until = :leaseUntil, updated_at = :now
                where export_id = :exportId
                """)
            .param("leaseUntil", OffsetDateTime.now().plusMinutes(1))
            .param("now", OffsetDateTime.now())
            .param("exportId", exportIds[1])
            .update();
        revoke(exportIds[1], "생성 중 범위 오류 발견");
        assertThat(jdbcClient.sql("select lease_owner from audit_export_job where export_id = :exportId")
            .param("exportId", exportIds[1]).query(String.class).optional()).isEmpty();
        assertThat(eventCount(exportIds[1], "DELETED")).isZero();

        jdbcClient.sql("""
                update audit_export_job
                set status = 'READY', content = :content, content_digest = :digest,
                    content_size = 1, content_type = 'text/csv', file_name = 'evidence.csv',
                    generated_at = :now, expires_at = :expiresAt, updated_at = :now
                where export_id = :exportId
                """)
            .param("content", new byte[] {1})
            .param("digest", "a".repeat(64))
            .param("now", OffsetDateTime.now())
            .param("expiresAt", OffsetDateTime.now().plusHours(1))
            .param("exportId", exportIds[2])
            .update();
        revoke(exportIds[2], "다운로드 전 승인 오류 발견");
        assertThat(jdbcClient.sql("select content from audit_export_job where export_id = :exportId")
            .param("exportId", exportIds[2]).query(byte[].class).optional()).isEmpty();
        assertThat(eventCount(exportIds[2], "DELETED")).isEqualTo(1);
    }

    @Test
    void ordinaryOperatorCanRequestAndReadOnlyOwnEvidenceExport() throws Exception {
        String marker = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String executionId = execute(marker, "approved context");
        String exportId = objectMapper.readTree(mockMvc.perform(post("/api/v1/audit-exports")
                .header("X-ADP-User-Id", "ordinary-operator")
                .header("X-ADP-User-Roles", "OPERATOR")
                .contentType(MediaType.APPLICATION_JSON)
                .content(exportRequest(executionId, "CSV", "forbidden-" + marker)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString()).path("exportId").asText();

        mockMvc.perform(get("/api/v1/audit-exports/{exportId}", exportId)
                .header("X-ADP-User-Id", "ordinary-operator")
                .header("X-ADP-User-Roles", "OPERATOR"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.job.requesterId").value("ordinary-operator"));

        mockMvc.perform(get("/api/v1/audit-exports/{exportId}", exportId)
                .header("X-ADP-User-Id", "different-operator")
                .header("X-ADP-User-Roles", "OPERATOR"))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/audit-exports")
                .param("view", "APPROVAL_QUEUE")
                .header("X-ADP-User-Id", "ordinary-operator")
                .header("X-ADP-User-Roles", "OPERATOR"))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/audit-exports")
                .param("view", "AUDIT_HISTORY")
                .header("X-ADP-User-Id", "ordinary-operator")
                .header("X-ADP-User-Roles", "OPERATOR"))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/audit-exports/work-summary")
                .header("X-ADP-User-Id", "ordinary-operator")
                .header("X-ADP-User-Roles", "OPERATOR"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.operationsAvailable").value(false))
            .andExpect(jsonPath("$.operations.pendingApproval").value(0))
            .andExpect(jsonPath("$.operations.oldestPendingAgeSeconds").doesNotExist());
    }

    @Test
    void exposesScopedMyWorkAndApprovalQueueWithoutSelfApprovalTasks() throws Exception {
        String marker = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String executionId = execute(marker, "approved context");
        String auditorExport = request(executionId, "CSV", "work-auditor-" + marker,
            "auditor-local", "AUDITOR").path("exportId").asText();
        String oldestAuditorExport = request(executionId, "CSV", "work-oldest-" + marker,
            "auditor-local", "AUDITOR").path("exportId").asText();
        String approverOwnedExport = request(executionId, "PDF", "work-approver-" + marker,
            "privileged-operator-local", "PRIVILEGED_OPERATOR").path("exportId").asText();
        jdbcClient.sql("update audit_export_job set created_at = :createdAt where export_id = :exportId")
            .param("createdAt", OffsetDateTime.now().minusHours(48))
            .param("exportId", oldestAuditorExport)
            .update();

        mockMvc.perform(get("/api/v1/audit-exports")
                .param("view", "MY_REQUESTS")
                .header("X-ADP-User-Id", "auditor-local")
                .header("X-ADP-User-Roles", "AUDITOR"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[*].exportId").value(org.hamcrest.Matchers.hasItem(auditorExport)))
            .andExpect(jsonPath("$.items[*].exportId").value(org.hamcrest.Matchers.hasItem(oldestAuditorExport)))
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

        var approvalItems = exportPersistence.searchWork(
            "institution_local", java.util.Set.of("*"), "privileged-operator-local", true,
            AuditExportWorkView.APPROVAL_QUEUE, null, 0, 100
        ).items();
        assertThat(approvalItems).extracting("exportId")
            .satisfies(ids -> assertThat(ids.indexOf(oldestAuditorExport)).isLessThan(ids.indexOf(auditorExport)));
        var requesterItems = exportPersistence.searchWork(
            "institution_local", java.util.Set.of("*"), "auditor-local", false,
            AuditExportWorkView.MY_REQUESTS, null, 0, 100
        ).items();
        assertThat(requesterItems).extracting("exportId")
            .satisfies(ids -> assertThat(ids.indexOf(oldestAuditorExport)).isLessThan(ids.indexOf(auditorExport)));
    }

    @Test
    void separatesActivePersonalWorkHandledDecisionsAndInstitutionAuditHistory() throws Exception {
        String marker = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String exportId = request(execute(marker, "approved context"), "CSV", "work-history-" + marker,
            "auditor-local", "AUDITOR").path("exportId").asText();

        mockMvc.perform(post("/api/v1/audit-exports/{exportId}/approval", exportId)
                .header("X-ADP-User-Id", "privileged-operator-local")
                .header("X-ADP-User-Roles", "PRIVILEGED_OPERATOR")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"APPROVE\",\"reason\":\"승인 범위 확인\"}"))
            .andExpect(status().isOk());
        assertThat(workerService.processNext("history-worker")).isTrue();
        mockMvc.perform(get("/api/v1/audit-exports/{exportId}/download", exportId)
                .header("X-ADP-User-Id", "auditor-local")
                .header("X-ADP-User-Roles", "AUDITOR"))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/audit-exports")
                .param("view", "MY_REQUESTS")
                .header("X-ADP-User-Id", "auditor-local")
                .header("X-ADP-User-Roles", "AUDITOR"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[*].exportId", org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.hasItem(exportId))));
        mockMvc.perform(get("/api/v1/audit-exports")
                .param("view", "MY_HISTORY")
                .header("X-ADP-User-Id", "auditor-local")
                .header("X-ADP-User-Roles", "AUDITOR"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[*].exportId").value(org.hamcrest.Matchers.hasItem(exportId)));
        mockMvc.perform(get("/api/v1/audit-exports")
                .param("view", "DECISION_HISTORY")
                .header("X-ADP-User-Id", "privileged-operator-local")
                .header("X-ADP-User-Roles", "PRIVILEGED_OPERATOR"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[*].exportId").value(org.hamcrest.Matchers.hasItem(exportId)));
        mockMvc.perform(get("/api/v1/audit-exports")
                .param("view", "AUDIT_HISTORY")
                .header("X-ADP-User-Id", "auditor-local")
                .header("X-ADP-User-Roles", "AUDITOR"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[*].exportId").value(org.hamcrest.Matchers.hasItem(exportId)));

        mockMvc.perform(get("/api/v1/audit-exports")
                .param("view", "AUDIT_HISTORY")
                .header("X-ADP-User-Id", "privileged-operator-local")
                .header("X-ADP-User-Roles", "PRIVILEGED_OPERATOR"))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/audit-exports")
                .param("view", "DECISION_HISTORY")
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

        mockMvc.perform(get("/api/v1/audit-exports/{exportId}/download", exportId)
                .header("X-ADP-User-Id", "privileged-operator-local")
                .header("X-ADP-User-Roles", "PRIVILEGED_OPERATOR"))
            .andExpect(status().isForbidden());

        byte[] pdf = mockMvc.perform(get("/api/v1/audit-exports/{exportId}/download", exportId)
                .header("X-ADP-User-Id", "auditor-local")
                .header("X-ADP-User-Roles", "AUDITOR"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "application/pdf"))
            .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        mockMvc.perform(get("/api/v1/audit-exports/{exportId}/download", exportId)
                .header("X-ADP-User-Id", "auditor-local")
                .header("X-ADP-User-Roles", "AUDITOR"))
            .andExpect(status().isOk());
        assertThat(eventCount(exportId, "DOWNLOADED")).isEqualTo(2);

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

    @Test
    void committedRevocationPreventsAWaitingDownloadFromReturningContent() throws Exception {
        String marker = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String exportId = request(execute(marker, "approved context"), "CSV", "download-race-" + marker,
            "auditor-local", "AUDITOR").path("exportId").asText();
        mockMvc.perform(post("/api/v1/audit-exports/{exportId}/approval", exportId)
                .header("X-ADP-User-Id", "privileged-operator-local")
                .header("X-ADP-User-Roles", "PRIVILEGED_OPERATOR")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"APPROVE\",\"reason\":\"동시성 검증 승인\"}"))
            .andExpect(status().isOk());
        assertThat(workerService.processNext("download-race-worker")).isTrue();

        CountDownLatch rowLocked = new CountDownLatch(1);
        CountDownLatch allowRevoke = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var revoke = executor.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                jdbcClient.sql("select export_id from audit_export_job where export_id = :exportId for update")
                    .param("exportId", exportId).query(String.class).single();
                rowLocked.countDown();
                try {
                    assertThat(allowRevoke.await(5, TimeUnit.SECONDS)).isTrue();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                }
                exportPersistence.revoke(exportId, "institution_local", Set.of("*"),
                    "privileged-operator-local", "동시 다운로드 차단", "req-race", "trace-race",
                    OffsetDateTime.now());
            }));
            assertThat(rowLocked.await(5, TimeUnit.SECONDS)).isTrue();
            var download = executor.submit(() -> mockMvc.perform(
                get("/api/v1/audit-exports/{exportId}/download", exportId)
                    .header("X-ADP-User-Id", "auditor-local")
                    .header("X-ADP-User-Roles", "AUDITOR")
            ).andReturn().getResponse().getStatus());

            allowRevoke.countDown();
            revoke.get(5, TimeUnit.SECONDS);
            assertThat(download.get(5, TimeUnit.SECONDS)).isEqualTo(409);
        }
        assertThat(eventCount(exportId, "REVOKED")).isEqualTo(1);
        assertThat(eventCount(exportId, "DOWNLOADED")).isZero();
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

    private void revoke(String exportId, String reason) throws Exception {
        mockMvc.perform(post("/api/v1/audit-exports/{exportId}/approval", exportId)
                .header("X-ADP-User-Id", "privileged-operator-local")
                .header("X-ADP-User-Roles", "PRIVILEGED_OPERATOR")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"REVOKE\",\"reason\":\"%s\"}".formatted(reason)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("REVOKED"))
            .andExpect(jsonPath("$.revocationReason").value(reason));
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
