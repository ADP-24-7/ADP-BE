package com.adp.gateway.auth.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
    "adp.local-user-auth.enabled=false"
})
@AutoConfigureMockMvc
class UserSessionControllerTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcClient jdbcClient;

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
}
