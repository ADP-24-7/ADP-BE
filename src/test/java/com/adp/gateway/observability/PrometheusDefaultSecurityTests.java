package com.adp.gateway.observability;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class PrometheusDefaultSecurityTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void requiresAuthenticationByDefault() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void deniesRuntimeExecutorAccessToGlobalOperationalMetrics() throws Exception {
        mockMvc.perform(get("/actuator/prometheus")
                .with(user("runtime").roles("RUNTIME_EXECUTOR")))
            .andExpect(status().isForbidden());
    }

    @Test
    void allowsAuditorAccessToGlobalOperationalMetrics() throws Exception {
        mockMvc.perform(get("/actuator/prometheus")
                .with(user("auditor").roles("AUDITOR")))
            .andExpect(status().isOk());
    }

    @Test
    void allowsOperatorAccessToGlobalOperationalMetrics() throws Exception {
        mockMvc.perform(get("/actuator/prometheus")
                .with(user("operator").roles("OPERATOR")))
            .andExpect(status().isOk());
    }
}
