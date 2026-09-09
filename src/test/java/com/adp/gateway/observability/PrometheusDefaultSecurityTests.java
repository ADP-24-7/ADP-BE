package com.adp.gateway.observability;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Set;

import com.adp.gateway.auth.domain.AdpRole;
import com.adp.gateway.auth.domain.AuthPrincipal;
import com.adp.gateway.auth.domain.AuthenticatedPrincipal;
import com.adp.gateway.auth.domain.PrincipalType;
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
    void deniesTenantAuditorAccessToGlobalOperationalMetrics() throws Exception {
        mockMvc.perform(get("/actuator/prometheus")
                .with(user("auditor").roles("AUDITOR")))
            .andExpect(status().isForbidden());
    }

    @Test
    void deniesTenantOperatorAccessToGlobalOperationalMetrics() throws Exception {
        mockMvc.perform(get("/actuator/prometheus")
                .with(user("operator").roles("OPERATOR")))
            .andExpect(status().isForbidden());
    }

    @Test
    void allowsDedicatedMetricsScraperAccessToGlobalOperationalMetrics() throws Exception {
        mockMvc.perform(get("/actuator/prometheus")
                .with(authentication(metricsScraper(PrincipalType.SERVICE))))
            .andExpect(status().isOk());
    }

    @Test
    void deniesUserPrincipalEvenWhenMetricsScraperRoleIsAssigned() throws Exception {
        mockMvc.perform(get("/actuator/prometheus")
                .with(authentication(metricsScraper(PrincipalType.USER))))
            .andExpect(status().isForbidden());
    }

    private AuthenticatedPrincipal metricsScraper(PrincipalType principalType) {
        return new AuthenticatedPrincipal(
            new AuthPrincipal(
                "prometheus", principalType, "prometheus", "platform", false,
                Set.of(), Set.of(AdpRole.METRICS_SCRAPER)
            ),
            List.of(() -> "ROLE_METRICS_SCRAPER")
        );
    }
}
