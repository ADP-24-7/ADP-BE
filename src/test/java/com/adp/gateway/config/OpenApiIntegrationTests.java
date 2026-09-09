package com.adp.gateway.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
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
            .andExpect(jsonPath("$.paths['/v1/runtime/executions'].post.parameters[0].name")
                .value("X-ADP-Request-Timestamp"))
            .andExpect(jsonPath("$.paths['/v1/runtime/executions'].post.parameters[0].required")
                .value(true))
            .andExpect(jsonPath("$.components.schemas.DigitalAssetRuntimeInput").exists())
            .andExpect(jsonPath("$.components.schemas.DigitalAssetOutboundRequest").exists())
            .andExpect(jsonPath("$.components.schemas.DigitalAssetRuntimeInput.required", hasItems(
                "approvedTransactionReference", "customerId", "accountId", "outboundRequest"
            )))
            .andExpect(jsonPath("$.components.schemas.DigitalAssetOutboundRequest.required", hasItems(
                "requestedAsset", "requestedAmount", "requestedDestination",
                "requestedBeneficiaryReference", "regulatoryOutboundData"
            )))
            .andExpect(jsonPath("$.components.schemas.DigitalAssetDescriptor.required", hasItems(
                "chainId", "assetKind", "assetSymbol", "operation"
            )))
            .andExpect(jsonPath(
                "$.components.schemas.DigitalAssetDescriptor.required",
                not(hasItem("assetContractAddress"))
            ))
            .andExpect(jsonPath(
                "$.components.schemas.DigitalAssetDescriptor.required",
                not(hasItem("tokenId"))
            ))
            .andExpect(jsonPath(
                "$.components.schemas.DigitalAssetOutboundRequest.properties.requestedAmount.pattern"
            ).value("(?!0+$)[0-9]{1,78}"))
            .andExpect(jsonPath(
                "$.components.schemas.DigitalAssetOutboundRequest.properties.regulatoryOutboundData.additionalProperties"
            ).value(false))
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
