package com.adp.gateway.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class OpenApiIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void exposesOpenApiContractWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.info.title").value("ADP Gateway Runtime API"))
            .andExpect(jsonPath("$.paths['/v1/runtime/executions']").exists())
            .andExpect(jsonPath("$.components.schemas.DigitalAssetRuntimeInput").exists())
            .andExpect(jsonPath("$.components.schemas.DigitalAssetOutboundRequest").exists())
            .andExpect(content().string(containsString("digitalAsset")))
            .andExpect(content().string(containsString("requestedBeneficiaryReference")))
            .andExpect(content().string(containsString("FUNGIBLE_TOKEN")))
            .andExpect(jsonPath("$.components.securitySchemes.adpApiKey.name").value("X-ADP-API-Key"));
    }

    @Test
    void exposesSwaggerUiWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
            .andExpect(status().is3xxRedirection());
    }

    @Test
    void redirectsRootAndDocsToSwaggerUiWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/swagger-ui/index.html"));

        mockMvc.perform(get("/docs"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/swagger-ui/index.html"));
    }
}
