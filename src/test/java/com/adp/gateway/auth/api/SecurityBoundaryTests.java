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
class SecurityBoundaryTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void adminApiDoesNotAcceptServiceApiKey() throws Exception {
        mockMvc.perform(get("/api/admin/not-created")
                .header("X-ADP-API-Key", "local-dev-api-key"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.reasonCode").value("AUTHENTICATION_FAILED"));
    }

    @Test
    void runtimeApiDoesNotAcceptUserHeaderCredential() throws Exception {
        mockMvc.perform(get("/api/runtime/not-created")
                .header("X-ADP-User-Id", "admin-local")
                .header("X-ADP-User-Roles", "OPERATOR"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.reasonCode").value("AUTHENTICATION_FAILED"));
    }

    @Test
    void adminApiAcceptsUserAuthenticationBoundaryBeforeRouting() throws Exception {
        mockMvc.perform(get("/api/admin/not-created")
                .header("X-ADP-User-Id", "admin-local")
                .header("X-ADP-User-Roles", "OPERATOR"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.reasonCode").value("MALFORMED_REQUEST"));
    }
}
