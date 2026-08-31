package com.adp.gateway.auth.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "adp.local-fixtures.enabled=true")
@AutoConfigureMockMvc
class AuthContextControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void returnsAuthenticatedPrincipalContext() throws Exception {
        mockMvc.perform(get("/api/internal/auth/context")
                .header("X-ADP-API-Key", "local-dev-api-key"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.principalId").value("svc_local_runtime"))
            .andExpect(jsonPath("$.principalType").value("SERVICE"))
            .andExpect(jsonPath("$.roles").isArray())
            .andExpect(jsonPath("$.workloadIds").isArray())
            .andExpect(jsonPath("$.subjectAuthorizationRequired").value(true));
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
}
