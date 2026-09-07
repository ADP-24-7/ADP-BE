package com.adp.gateway.ai;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.stream.Stream;

import com.adp.gateway.ai.application.AiModelProfileCatalog;
import com.adp.gateway.ai.domain.AiModelProfile;
import com.adp.gateway.ai.application.AiEvaluationRunCatalog;
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
                      "evaluationRunId":"%s",
                      "evalCaseId":"%s",
                      "processingContexts":["AI_USE"],
                      "input":{"prompt":"승인된 고객 정보를 간단히 요약하세요"}
                    }
                    """.formatted(
                        catalog.approvalReference(profile), profile.destinationProfileId(), suffix,
                        AiEvaluationRunCatalog.BASELINE_RUN_ID, AiEvaluationRunCatalog.BASELINE_CASE_ID
                    )))
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
            .andExpect(jsonPath("$.snapshotDigest").value(catalog.policySnapshotDigest()))
            .andExpect(jsonPath("$.evidence.destinationProfileId").value(profile.destinationProfileId()))
            .andExpect(jsonPath("$.evidence.destinationProfileVersion").value(profile.destinationProfileVersion()))
            .andExpect(jsonPath("$.evidence.aiModel.profileId").value(profile.profileId()))
            .andExpect(jsonPath("$.evidence.aiModel.profileVersion").value(profile.profileVersion()))
            .andExpect(jsonPath("$.evidence.aiModel.profileDigest").value(profile.modelProfileDigest()))
            .andExpect(jsonPath("$.evidence.aiModel.providerModelId").value(profile.modelId()))
            .andExpect(jsonPath("$.evidence.aiModel.providerModelVersion").value(profile.modelVersion()))
            .andExpect(jsonPath("$.evidence.aiModel.connectionProfileId")
                .value(profile.providerConnectionProfileId()))
            .andExpect(jsonPath("$.evidence.aiModel.maxTokens").value(profile.maxTokens()))
            .andExpect(jsonPath("$.evidence.aiModel.temperature").value(profile.temperature()))
            .andExpect(jsonPath("$.evidence.aiModel.samplingProfileVersion").value(profile.profileVersion()))
            .andExpect(jsonPath("$.evidence.aiModel.evaluationRunId")
                .value(AiEvaluationRunCatalog.BASELINE_RUN_ID))
            .andExpect(jsonPath("$.evidence.aiModel.evalCaseId")
                .value(AiEvaluationRunCatalog.BASELINE_CASE_ID))
            .andExpect(jsonPath("$.evidence.aiModel.evaluationContractDigest")
                .value(org.hamcrest.Matchers.matchesPattern("sha256:[0-9a-f]{64}")))
            .andExpect(jsonPath("$.evidence.aiModel.expectedInputDigest")
                .value(org.hamcrest.Matchers.matchesPattern("[0-9a-f]{64}")))
            .andExpect(jsonPath("$.evidence.aiModel.actualInputDigest")
                .value(org.hamcrest.Matchers.matchesPattern("[0-9a-f]{64}")))
            .andExpect(jsonPath("$.evidence.aiModel.datasetVersion")
                .value("financial_synthetic_processed_v1"))
            .andExpect(jsonPath("$.evidence.aiModel.policySnapshotDigest")
                .value(catalog.policySnapshotDigest()))
            .andExpect(jsonPath("$.evidence.aiModel.destinationProfileDigest")
                .value(profile.destinationProfileDigest()));
    }

    private static Stream<Integer> modelIndexes() {
        return Stream.of(0, 1, 2);
    }
}
