package com.adp.gateway.ai;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.stream.Stream;

import com.adp.gateway.ai.application.AiModelProfileCatalog;
import com.adp.gateway.ai.domain.AiModelProfile;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
    "adp.local-fixtures.enabled=true",
    "adp.mock-runtime.enabled=true"
})
@AutoConfigureMockMvc
class AiModelProfileRuntimeTests {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AiModelProfileCatalog catalog;

    @Autowired
    private ObjectMapper objectMapper;

    @ParameterizedTest
    @MethodSource("modelIndexes")
    void executesAllAllowlistedModelProfilesThroughExistingAiRuntime(int modelIndex) throws Exception {
        AiModelProfile profile = catalog.profiles().get(modelIndex);
        String suffix = profile.profileId().replace('.', '-');

        String response = mockMvc.perform(post("/v1/runtime/executions")
                .header("X-Request-Id", "req_eval_" + modelIndex)
                .header("X-Trace-Id", "trace_eval_" + modelIndex)
                .header("X-ADP-API-Key", "local-dev-api-key")
                .contentType("application/json")
                .content("""
                    {
                      "institutionId":"institution_local",
                      "approvalReference":"%s",
                      "workloadId":"customer_summary",
                      "purposeCode":"CUSTOMER_SUPPORT",
                      "subjectScope":"customer:customer-100",
                      "destinationProfileId":"%s",
                      "idempotencyKey":"idem_eval_%s",
                      "processingContexts":["AI_USE"],
                      "input":{"prompt":"승인된 고객 정보를 간단히 요약하세요"}
                    }
                    """.formatted(catalog.approvalReference(profile), profile.destinationProfileId(), suffix)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.connectorStatus").value("ACKNOWLEDGED"))
            .andExpect(jsonPath("$.responseGuardStatus").value("PASSED"))
            .andReturn().getResponse().getContentAsString();

        String executionId = objectMapper.readTree(response).path("executionId").asText();
        mockMvc.perform(get("/v1/runtime/executions/{executionId}", executionId)
                .header("X-ADP-API-Key", "local-dev-api-key"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.providerProfileId").value(profile.profileId()))
            .andExpect(jsonPath("$.evidence.destinationProfileId").value(profile.destinationProfileId()))
            .andExpect(jsonPath("$.evidence.destinationProfileVersion").value(profile.destinationProfileVersion()));
    }

    private static Stream<Integer> modelIndexes() {
        return Stream.of(0, 1, 2);
    }
}
