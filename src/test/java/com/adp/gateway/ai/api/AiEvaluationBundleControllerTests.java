package com.adp.gateway.ai.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import java.util.Set;

import com.adp.gateway.ai.application.AiEvaluationBundlePort;
import com.adp.gateway.ai.application.AiEvaluationRunCatalog;
import com.adp.gateway.ai.application.AiModelProfileCatalog;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
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
class AiEvaluationBundleControllerTests {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AiModelProfileCatalog modelProfiles;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AiEvaluationBundlePort bundlePort;

    @Test
    void privilegedOperatorExportsDaConsumableEvaluationBundle() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String executionId = submitEvaluation(suffix);

        String firstResponse = export("PRIVILEGED_OPERATOR")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.manifest.schema_version").value("adp-ai-evaluation-bundle/v1"))
            .andExpect(jsonPath("$.manifest.content_digest")
                .value(org.hamcrest.Matchers.matchesPattern("sha256:[0-9a-f]{64}")))
            .andExpect(jsonPath("$.execution_config.evaluation_run_id")
                .value(AiEvaluationRunCatalog.BASELINE_RUN_ID))
            .andExpect(jsonPath("$.execution_config.dataset_digest")
                .value(org.hamcrest.Matchers.matchesPattern("sha256:[0-9a-f]{64}")))
            .andExpect(jsonPath("$.case_results[?(@.execution_id == '%s')]", executionId).exists())
            .andExpect(jsonPath("$.runtime_metrics[?(@.execution_id == '%s')]", executionId).exists())
            .andExpect(jsonPath("$.trace_index[?(@.execution_id == '%s')]", executionId).exists())
            .andReturn().getResponse().getContentAsString();

        String secondResponse = export("PRIVILEGED_OPERATOR")
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        JsonNode first = objectMapper.readTree(firstResponse);
        JsonNode second = objectMapper.readTree(secondResponse);

        assertThat(first.path("manifest").path("content_digest").asText())
            .isEqualTo(second.path("manifest").path("content_digest").asText());
        var digestContent = objectMapper.createObjectNode();
        digestContent.put("schema_version", first.path("manifest").path("schema_version").asText());
        digestContent.set("execution_config", first.path("execution_config"));
        digestContent.set("case_results", first.path("case_results"));
        digestContent.set("runtime_metrics", first.path("runtime_metrics"));
        digestContent.set("trace_index", first.path("trace_index"));
        String recomputedDigest = "sha256:" + HexFormat.of().formatHex(
            MessageDigest.getInstance("SHA-256").digest(objectMapper.writeValueAsBytes(digestContent))
        );
        assertThat(first.path("manifest").path("content_digest").asText()).isEqualTo(recomputedDigest);
        assertThat(firstResponse)
            .doesNotContain("승인된 고객 정보를 간단히 요약하세요")
            .doesNotContain("customer-100")
            .doesNotContain("local-dev-api-key")
            .doesNotContain("req_eval_bundle_")
            .doesNotContain("trace_eval_bundle_");

        assertThat(bundlePort.load(AiEvaluationRunCatalog.BASELINE_RUN_ID, "other-institution", Set.of("*")))
            .isEmpty();
        assertThat(bundlePort.load(
            AiEvaluationRunCatalog.BASELINE_RUN_ID, "institution_local", Set.of("fraud_detection")
        )).isEmpty();
    }

    @Test
    void operatorCannotExportEvaluationBundle() throws Exception {
        export("OPERATOR").andExpect(status().isForbidden());
    }

    @Test
    void inaccessibleEvaluationRunReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/admin/ai/evaluation-runs/{runId}/bundle", "missing-run")
                .header("X-ADP-User-Id", "privileged-local")
                .header("X-ADP-User-Roles", "PRIVILEGED_OPERATOR"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.reasonCode").value("AI_EVALUATION_BUNDLE_NOT_FOUND"));
    }

    private org.springframework.test.web.servlet.ResultActions export(String role) throws Exception {
        return mockMvc.perform(get(
                "/api/admin/ai/evaluation-runs/{runId}/bundle", AiEvaluationRunCatalog.BASELINE_RUN_ID
            )
            .header("X-ADP-User-Id", "bundle-exporter")
            .header("X-ADP-User-Roles", role));
    }

    private String submitEvaluation(String suffix) throws Exception {
        var profile = modelProfiles.profiles().getFirst();
        String response = mockMvc.perform(post("/v1/runtime/executions")
                .header("X-Request-Id", "req_eval_bundle_" + suffix)
                .header("X-Trace-Id", "trace_eval_bundle_" + suffix)
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
                      "idempotencyKey":"idem_eval_bundle_%s",
                      "evaluationRunId":"%s",
                      "evalCaseId":"%s",
                      "processingContexts":["AI_USE"],
                      "input":{"prompt":"승인된 고객 정보를 간단히 요약하세요"}
                    }
                    """.formatted(
                        modelProfiles.approvalReference(profile), profile.destinationProfileId(), suffix,
                        AiEvaluationRunCatalog.BASELINE_RUN_ID, AiEvaluationRunCatalog.BASELINE_CASE_ID
                    )))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("executionId").asText();
    }
}
