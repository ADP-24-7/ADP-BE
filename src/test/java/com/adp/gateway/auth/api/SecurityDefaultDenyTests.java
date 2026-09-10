package com.adp.gateway.auth.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
    "adp.observability.prometheus-public=false",
    "adp.local-fixtures.enabled=true",
    "adp.local-user-auth.enabled=true"
})
@AutoConfigureMockMvc
class SecurityDefaultDenyTests {
    @Autowired
    private MockMvc mockMvc;

    @ParameterizedTest
    @MethodSource("protectedPaths")
    void requiresAuthenticationOutsideExplicitPublicAllowlist(String path) throws Exception {
        mockMvc.perform(get(path))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.reasonCode").value("AUTHENTICATION_FAILED"));
    }

    @Test
    void requiresAuthenticationForRuntimeCommands() throws Exception {
        mockMvc.perform(post("/v1/runtime/executions"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.reasonCode").value("AUTHENTICATION_FAILED"));
    }

    @Test
    void doesNotAcceptServiceCredentialOutsideTheStatelessServiceBoundary() throws Exception {
        mockMvc.perform(get("/not-explicitly-matched")
                .header("X-ADP-API-Key", "local-dev-api-key"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.reasonCode").value("AUTHENTICATION_FAILED"));
    }

    @Test
    void deniesUnmatchedEndpointToAuthenticatedAdminPrincipal() throws Exception {
        mockMvc.perform(get("/not-explicitly-matched")
                .with(user("admin-local").roles("OPERATOR")))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.reasonCode").value("AUTHORIZATION_DENIED"));
    }

    @ParameterizedTest
    @MethodSource("publicOkPaths")
    void servesExplicitPublicEndpoints(String path) throws Exception {
        mockMvc.perform(get(path)).andExpect(status().isOk());
    }

    @Test
    void redirectsPublicDocsShortcutToSwaggerUi() throws Exception {
        mockMvc.perform(get("/docs"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/swagger-ui/index.html"));
    }

    private static Stream<String> protectedPaths() {
        return Stream.of(
            "/api/runtime/not-created",
            "/api/admin/not-created",
            "/api/privileged/not-created",
            "/actuator/prometheus",
            "/not-explicitly-public"
        );
    }

    private static Stream<String> publicOkPaths() {
        return Stream.of(
            "/actuator/health",
            "/actuator/info",
            "/api/internal/info",
            "/v3/api-docs"
        );
    }
}
