package com.adp.gateway.ai;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
    "adp.local-fixtures.enabled=true",
    "adp.mock-runtime.enabled=true",
    "adp.local-user-auth.enabled=true"
})
@AutoConfigureMockMvc
class AiModelProfileRuntimeTests {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AiModelProfileCatalog catalog;

    @Autowired
    private ObjectMapper objectMapper;

    @org.junit.jupiter.api.BeforeEach
    void freezeEvaluationContract() throws Exception {
        mockMvc.perform(post("/api/admin/ai/evaluation-runs/{runId}/contract/freeze",
                AiEvaluationRunCatalog.BASELINE_RUN_ID)
                .header("X-ADP-User-Id", "privileged-local")
                .header("X-ADP-User-Roles", "PRIVILEGED_OPERATOR"))
            .andExpect(status().isOk());
    }

    @Test
    void replaysIdenticalEvaluationRequestWithTheResolvedContract() throws Exception {
        AiModelProfile profile = catalog.profiles().getFirst();
        String idempotencyKey = "idem_eval_replay";

        String first = submitEvaluation(profile, idempotencyKey, "req_eval_replay_1", "trace_eval_replay_1");
        String replay = submitEvaluation(profile, idempotencyKey, "req_eval_replay_2", "trace_eval_replay_2");

        assertThat(objectMapper.readTree(replay).path("executionId").asText())
            .isEqualTo(objectMapper.readTree(first).path("executionId").asText());
        assertThat(objectMapper.readTree(replay).path("replayed").asBoolean()).isTrue();
    }

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
                .value(profile.destinationProfileDigest()))
            .andExpect(jsonPath("$.evidence.aiModel.measurementType").value("MOCK"))
            .andExpect(jsonPath("$.evidence.aiModel.fullResponseLatencyMillis").value(0))
            .andExpect(jsonPath("$.evidence.aiModel.attemptElapsedMillis").doesNotExist())
            .andExpect(jsonPath("$.evidence.aiModel.tokenUsageStatus").value("NOT_PROVIDED"))
            .andExpect(jsonPath("$.evidence.aiModel.initialRuntimeLatencyMillis").isNumber())
            .andExpect(jsonPath("$.evidence.aiModel.runtimeFinalAction").value("TRANSFORM"))
            .andExpect(jsonPath("$.evidence.aiModel.responseGuardStatus").value("PASSED"))
            .andExpect(jsonPath("$.evidence.aiModel.providerStatus").value("ACKNOWLEDGED"))
            .andExpect(jsonPath("$.evidence.aiModel.errorCategory").value("NONE"))
            .andExpect(jsonPath("$.evidence.aiModel.evidenceStatus").value("COMPLETE"))
            .andExpect(jsonPath("$.evidence.aiModel.traceReference").value(executionId));
    }

    private static Stream<Integer> modelIndexes() {
        return Stream.of(0, 1, 2);
    }

    private String submitEvaluation(
        AiModelProfile profile,
        String idempotencyKey,
        String requestId,
        String traceId
    ) throws Exception {
        return mockMvc.perform(post("/v1/runtime/executions")
                .header("X-Request-Id", requestId)
                .header("X-Trace-Id", traceId)
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
                      "idempotencyKey":"%s",
                      "evaluationRunId":"%s",
                      "evalCaseId":"%s",
                      "processingContexts":["AI_USE"],
                      "input":{"prompt":"승인된 고객 정보를 간단히 요약하세요"}
                    }
                    """.formatted(
                        catalog.approvalReference(profile), profile.destinationProfileId(), idempotencyKey,
                        AiEvaluationRunCatalog.BASELINE_RUN_ID, AiEvaluationRunCatalog.BASELINE_CASE_ID
                    )))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    }
}
