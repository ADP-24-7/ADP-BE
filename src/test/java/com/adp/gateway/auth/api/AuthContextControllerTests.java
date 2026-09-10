package com.adp.gateway.auth.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.adp.gateway.auth.infrastructure.LocalAuthFixtureLoader;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
    "adp.local-fixtures.enabled=true",
    "adp.local-user-auth.enabled=true"
})
@AutoConfigureMockMvc
class AuthContextControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private LocalAuthFixtureLoader fixtureLoader;

    @Test
    void returnsAuthenticatedPrincipalContext() throws Exception {
        mockMvc.perform(get("/api/internal/auth/context")
                .header("X-ADP-API-Key", "local-dev-api-key"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.principalId").value("svc_local_runtime"))
            .andExpect(jsonPath("$.principalType").value("SERVICE"))
            .andExpect(jsonPath("$.displayName").isNotEmpty())
            .andExpect(jsonPath("$.institutionId").value("institution_local"))
            .andExpect(jsonPath("$.roles").isArray())
            .andExpect(jsonPath("$.workloadIds").isArray())
            .andExpect(jsonPath("$.subjectAuthorizationRequired").value(true));
    }

    @Test
    void returnsAdminUserContextThroughAdminAuthenticationBoundary() throws Exception {
        mockMvc.perform(get("/api/admin/auth/context")
                .header("X-ADP-User-Id", "operator-local")
                .header("X-ADP-User-Roles", "OPERATOR,PRIVILEGED_OPERATOR"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.principalId").value("operator-local"))
            .andExpect(jsonPath("$.principalType").value("USER"))
            .andExpect(jsonPath("$.displayName").value("operator-local"))
            .andExpect(jsonPath("$.institutionId").value("institution_local"))
            .andExpect(jsonPath("$.roles").isArray())
            .andExpect(jsonPath("$.workloadIds[0]").value("*"));
    }

    @Test
    void rejectsMissingAdminIdentityWithCommonErrorResponse() throws Exception {
        mockMvc.perform(get("/api/admin/auth/context")
                .header("X-Request-Id", "req_missing_admin_auth")
                .header("X-Trace-Id", "trace_missing_admin_auth"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.errorCode").value("AUTHENTICATION_FAILED"))
            .andExpect(jsonPath("$.requestId").value("req_missing_admin_auth"))
            .andExpect(jsonPath("$.traceId").value("trace_missing_admin_auth"));
    }

    @Test
    void rejectsMissingApiKeyWithCommonErrorResponse() throws Exception {
        mockMvc.perform(get("/api/internal/auth/context")
                .header("X-Request-Id", "req_missing_auth")
                .header("X-Trace-Id", "trace_missing_auth"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.errorCode").value("AUTHENTICATION_FAILED"))
            .andExpect(jsonPath("$.reasonCode").value("AUTHENTICATION_FAILED"))
            .andExpect(jsonPath("$.message").value("Authentication required"))
            .andExpect(jsonPath("$.requestId").value("req_missing_auth"))
            .andExpect(jsonPath("$.traceId").value("trace_missing_auth"));
    }

    @Test
    void rejectsInvalidApiKeyWithCommonErrorResponse() throws Exception {
        mockMvc.perform(get("/api/internal/auth/context")
                .header("X-Request-Id", "req_invalid_auth")
                .header("X-Trace-Id", "trace_invalid_auth")
                .header("X-ADP-API-Key", "wrong-key"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.errorCode").value("AUTHENTICATION_FAILED"))
            .andExpect(jsonPath("$.reasonCode").value("AUTHENTICATION_FAILED"))
            .andExpect(jsonPath("$.message").value("Authentication required"))
            .andExpect(jsonPath("$.requestId").value("req_invalid_auth"))
            .andExpect(jsonPath("$.traceId").value("trace_invalid_auth"));
    }

    @Test
    void repairsLocalPrincipalInstitutionAfterPreviousVersionUpgrade() {
        jdbcClient.sql("update auth_principal set institution_id = null where principal_id = 'svc_local_runtime'")
            .update();

        fixtureLoader.onApplicationEvent(null);

        String institutionId = jdbcClient.sql("""
                select institution_id from auth_principal where principal_id = 'svc_local_runtime'
                """)
            .query(String.class)
            .single();
        org.assertj.core.api.Assertions.assertThat(institutionId).isEqualTo("institution_local");
    }
}
