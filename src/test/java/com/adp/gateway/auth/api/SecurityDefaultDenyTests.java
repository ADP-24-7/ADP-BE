package com.adp.gateway.auth.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "adp.observability.prometheus-public=false")
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

    @ParameterizedTest
    @MethodSource("publicPaths")
    void permitsExplicitPublicEndpoints(String path) throws Exception {
        mockMvc.perform(get(path))
            .andExpect(result -> {
                int status = result.getResponse().getStatus();
                if (status == 401 || status == 403) {
                    throw new AssertionError("Public endpoint was blocked: " + path + " status=" + status);
                }
            });
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

    private static Stream<String> publicPaths() {
        return Stream.of(
            "/actuator/health",
            "/actuator/info",
            "/api/internal/info",
            "/docs",
            "/v3/api-docs"
        );
    }
}

