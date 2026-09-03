package com.adp.gateway.observability;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class PrometheusEndpointTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private GatewayObservability observability;

    @Test
    void exposesPrometheusMetricsWithoutCredentials() throws Exception {
        observability.recovery("RECONCILED");
        mockMvc.perform(get("/actuator/prometheus"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("jvm_info")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("adp_recovery_processing_total")));
    }
}
