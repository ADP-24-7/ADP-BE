package com.adp.gateway.auth.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.adp.gateway.auditexport.application.AuditExportWorkerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
    "adp.local-fixtures.enabled=true",
    "adp.local-user-auth.enabled=false",
    "adp.mock-runtime.enabled=true",
    "adp.audit-export.scheduler.enabled=false"
})
@AutoConfigureMockMvc
class UserSessionControllerTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcClient jdbcClient;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AuditExportWorkerService auditExportWorkerService;

    @AfterEach
    void restorePrincipals() {
        jdbcClient.sql("update auth_principal set enabled = true where principal_id = 'auditor-local'").update();
        jdbcClient.sql("""
                update auth_user_credential
                set failed_attempts = 0, locked_until = null
                where principal_id in ('auditor-local', 'privileged-operator-local')
                """).update();
    }

    @Test
    void rejectsInvalidPasswordWithoutCreatingSession() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"principalId\":\"auditor-local\",\"password\":\"wrong\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.reasonCode").value("AUTHENTICATION_FAILED"));
    }

    @Test
    void rejectsDisabledPrincipal() throws Exception {
        jdbcClient.sql("update auth_principal set enabled = false where principal_id = 'auditor-local'").update();

        mockMvc.perform(post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"principalId\":\"auditor-local\",\"password\":\"auditor-demo\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void createsSessionFromServerOwnedPrincipalAndIgnoresForgedRoles() throws Exception {
        var result = mockMvc.perform(post("/api/auth/login")
                .with(csrf())
                .header("X-ADP-User-Roles", "PRIVILEGED_OPERATOR")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"principalId":"auditor-local","password":"auditor-demo","roles":["PRIVILEGED_OPERATOR"]}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.principalId").value("auditor-local"))
            .andExpect(jsonPath("$.roles[0]").value("AUDITOR"))
            .andReturn();

        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(session).isNotNull();
        mockMvc.perform(get("/api/auth/me").session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.roles[0]").value("AUDITOR"));
    }

    @Test
    void localOperationsAccountDoesNotInheritApprovalRole() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"principalId\":\"operator-local\",\"password\":\"operator-demo\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.roles").value(org.hamcrest.Matchers.contains("OPERATOR")))
            .andExpect(jsonPath("$.roles", org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.hasItem("PRIVILEGED_OPERATOR"))));
    }

    @Test
    void rejectsSessionMutationWithoutCsrf() throws Exception {
        MockHttpSession session = login("auditor-local", "auditor-demo");

        mockMvc.perform(post("/api/v1/audit-exports")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.reasonCode").value("AUTHORIZATION_DENIED"));
    }

    @Test
    void auditorSessionCanReadTheAuditWorkspaceWithoutOperatorRole() throws Exception {
        MockHttpSession session = login("auditor-local", "auditor-demo");

        mockMvc.perform(get("/api/admin/audit/executions").session(session))
            .andExpect(status().isOk());
    }

    @Test
    void logoutInvalidatesExistingSession() throws Exception {
        MockHttpSession session = login("auditor-local", "auditor-demo");

        mockMvc.perform(post("/api/auth/logout").session(session).with(csrf()))
            .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/auth/me").session(session))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void loginRequiresCsrfProtection() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"principalId\":\"auditor-local\",\"password\":\"auditor-demo\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void locksAfterFiveFailuresAndAllowsLoginAfterLockExpires() throws Exception {
        for (int attempt = 1; attempt <= 5; attempt++) {
            mockMvc.perform(post("/api/auth/login")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"principalId\":\"auditor-local\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());
        }

        var credential = jdbcClient.sql("""
                select failed_attempts, locked_until
                from auth_user_credential
                where principal_id = 'auditor-local'
                """)
            .query((rs, rowNum) -> new Object[] {
                rs.getInt("failed_attempts"),
                rs.getObject("locked_until", OffsetDateTime.class)
            })
            .single();
        assertThat(credential[0]).isEqualTo(5);
        assertThat((OffsetDateTime) credential[1]).isAfter(OffsetDateTime.now());

        mockMvc.perform(post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"principalId\":\"auditor-local\",\"password\":\"auditor-demo\"}"))
            .andExpect(status().isUnauthorized());

        jdbcClient.sql("""
                update auth_user_credential
                set locked_until = :expired
                where principal_id = 'auditor-local'
                """)
            .param("expired", OffsetDateTime.now().minusSeconds(1))
            .update();
        login("auditor-local", "auditor-demo");
    }

    @Test
    void completesAuditExportThroughSeparateAuditorAndCheckerSessions() throws Exception {
        String marker = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String rawPrompt = "session-export-sensitive-" + marker;
        String executionId = execute(marker, rawPrompt);

        MockHttpSession auditorSession = login("auditor-local", "auditor-demo");
        String requestResponse = mockMvc.perform(post("/api/v1/audit-exports")
                .session(auditorSession)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(exportRequest(executionId, marker)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("REQUESTED"))
            .andReturn().getResponse().getContentAsString();
        String exportId = objectMapper.readTree(requestResponse).path("exportId").asText();

        mockMvc.perform(post("/api/auth/logout").session(auditorSession).with(csrf()))
            .andExpect(status().isNoContent());

        MockHttpSession checkerSession = login("privileged-operator-local", "approver-demo");
        mockMvc.perform(get("/api/v1/audit-exports/{exportId}", exportId).session(checkerSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.job.requesterId").value("auditor-local"))
            .andExpect(jsonPath("$.job.status").value("REQUESTED"));
        mockMvc.perform(post("/api/v1/audit-exports/{exportId}/approval", exportId)
                .session(checkerSession)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"APPROVE\",\"reason\":\"세션 분리 승인 검증\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("APPROVED"));

        assertThat(auditExportWorkerService.processNext("session-e2e-worker")).isTrue();
        mockMvc.perform(post("/api/auth/logout").session(checkerSession).with(csrf()))
            .andExpect(status().isNoContent());

        MockHttpSession downloadSession = login("auditor-local", "auditor-demo");
        byte[] content = mockMvc.perform(get("/api/v1/audit-exports/{exportId}/download", exportId)
                .session(downloadSession))
            .andExpect(status().isOk())
            .andExpect(header().exists("X-Content-SHA256"))
            .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(content, StandardCharsets.UTF_8))
            .contains(executionId, "INTERNAL-PRIVACY-SAFE-EVIDENCE")
            .doesNotContain(rawPrompt);
        assertThat(jdbcClient.sql("""
                select action from audit_export_event
                where export_id = :exportId
                order by occurred_at, event_id
                """)
            .param("exportId", exportId)
            .query(String.class)
            .list())
            .containsExactlyElementsOf(List.of("REQUESTED", "APPROVED", "GENERATED", "DOWNLOADED"));
    }

    private MockHttpSession login(String principalId, String password) throws Exception {
        var result = mockMvc.perform(post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"principalId":"%s","password":"%s"}
                    """.formatted(principalId, password)))
            .andExpect(status().isOk())
            .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private String execute(String marker, String prompt) throws Exception {
        String response = mockMvc.perform(post("/v1/runtime/executions")
                .header("X-Request-Id", "req_session_export_" + marker)
                .header("X-Trace-Id", "trace_session_export_" + marker)
                .header("X-ADP-API-Key", "local-dev-api-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"institutionId":"institution_local","approvalReference":"approval_ai_customer_support_v1",
                     "workloadId":"customer_summary","purposeCode":"CUSTOMER_SUPPORT",
                     "subjectScope":"customer:customer-100",
                     "destinationProfileId":"dest_internal_provider_project_provisional",
                     "idempotencyKey":"session-runtime-%s","processingContexts":["AI_USE"],
                     "input":{"prompt":"%s"}}
                    """.formatted(marker, prompt)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("executionId").asText();
    }

    private String exportRequest(String executionId, String marker) {
        return """
            {"executionId":"%s","reportType":"EXECUTION_EVIDENCE","format":"CSV",
             "reason":"세션 기반 감사 증적 제출","idempotencyKey":"session-export-%s"}
            """.formatted(executionId, marker);
    }
}
