package com.adp.gateway.ai.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.adp.gateway.ai.application.AiEvaluationBundleCanonicalizer;
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

    @Autowired
    private AiEvaluationBundleCanonicalizer canonicalizer;

    @Test
    void privilegedOperatorExportsDaConsumableEvaluationBundle() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        var executionIds = new java.util.ArrayList<String>();
        for (int index = 0; index < modelProfiles.profiles().size(); index++) {
            executionIds.add(submitEvaluation(suffix + "_" + index, index));
        }

        String firstResponse = export("PRIVILEGED_OPERATOR")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.manifest.schema_version").value("adp-ai-evaluation-bundle/v1"))
            .andExpect(jsonPath("$.manifest.content_digest")
                .value(org.hamcrest.Matchers.matchesPattern("sha256:[0-9a-f]{64}")))
            .andExpect(jsonPath("$.manifest.generated_at").isString())
            .andExpect(jsonPath("$.manifest.execution_cutoff_at").isString())
            .andExpect(jsonPath("$.execution_config.evaluation_run_id")
                .value(AiEvaluationRunCatalog.BASELINE_RUN_ID))
            .andExpect(jsonPath("$.execution_config.dataset_digest")
                .value(org.hamcrest.Matchers.matchesPattern("sha256:[0-9a-f]{64}")))
            .andExpect(jsonPath("$.manifest.execution_count").value(3))
            .andExpect(jsonPath("$.manifest.model_count").value(3))
            .andExpect(jsonPath("$.manifest.case_count").value(1))
            .andExpect(jsonPath("$.failure_summary.evaluated_execution_count").value(3))
            .andExpect(jsonPath("$.failure_summary.failed").value(0))
            .andReturn().getResponse().getContentAsString();

        String secondResponse = export("PRIVILEGED_OPERATOR")
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        JsonNode first = objectMapper.readTree(firstResponse);
        JsonNode second = objectMapper.readTree(secondResponse);
        var daBundle = new DaEvaluationBundleParserFixture(objectMapper).parse(firstResponse);

        assertThat(daBundle.evaluationRunId()).isEqualTo(AiEvaluationRunCatalog.BASELINE_RUN_ID);
        assertThat(daBundle.executionCount()).isEqualTo(3);
        assertThat(daBundle.modelCount()).isEqualTo(3);
        assertThat(daBundle.evaluatedExecutionCount()).isEqualTo(3);
        assertThat(first.path("manifest").path("content_digest").asText())
            .isEqualTo(second.path("manifest").path("content_digest").asText());
        Map<String, Object> digestContent = Map.of(
            "schema_version", first.path("manifest").path("schema_version").asText(),
            "execution_config", objectMapper.convertValue(first.path("execution_config"), Object.class),
            "case_results", objectMapper.convertValue(first.path("case_results"), Object.class),
            "runtime_metrics", objectMapper.convertValue(first.path("runtime_metrics"), Object.class),
            "failure_summary", objectMapper.convertValue(first.path("failure_summary"), Object.class),
            "trace_index", objectMapper.convertValue(first.path("trace_index"), Object.class)
        );
        String recomputedDigest = canonicalizer.digest(digestContent);
        assertThat(first.path("manifest").path("content_digest").asText()).isEqualTo(recomputedDigest);
        assertDaParserRejectsInconsistentIdentity(first);
        executionIds.forEach(executionId -> {
            assertThat(first.path("case_results").toString()).contains(executionId);
            assertThat(first.path("runtime_metrics").toString()).contains(executionId);
            assertThat(first.path("trace_index").toString()).contains(executionId);
        });
        assertThat(firstResponse)
            .doesNotContain("승인된 고객 정보를 간단히 요약하세요")
            .doesNotContain("customer-100")
            .doesNotContain("local-dev-api-key")
            .doesNotContain("req_eval_bundle_")
            .doesNotContain("trace_eval_bundle_");

        assertThat(bundlePort.load(
            AiEvaluationRunCatalog.BASELINE_RUN_ID, "other-institution", Set.of("*"), 10
        ))
            .isEmpty();
        assertThat(bundlePort.load(
            AiEvaluationRunCatalog.BASELINE_RUN_ID, "institution_local", Set.of("fraud_detection"), 10
        )).isEmpty();

        String latestFirstModelExecution = submitEvaluation(suffix + "_latest", 0);
        String latestResponse = export("PRIVILEGED_OPERATOR")
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        JsonNode latest = objectMapper.readTree(latestResponse);
        assertThat(latest.path("case_results").toString()).contains(latestFirstModelExecution);
        assertThat(latest.path("case_results").toString()).doesNotContain(executionIds.getFirst());
        assertThat(latest.path("manifest").path("bundle_id").asText())
            .isEqualTo(first.path("manifest").path("bundle_id").asText());
        assertThat(latest.path("manifest").path("content_digest").asText())
            .isNotEqualTo(first.path("manifest").path("content_digest").asText());
    }

    private void assertDaParserRejectsInconsistentIdentity(JsonNode validBundle) throws Exception {
        var parser = new DaEvaluationBundleParserFixture(objectMapper);

        JsonNode mismatchedExecution = validBundle.deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) mismatchedExecution.path("runtime_metrics").get(0))
            .put("execution_id", "exec-mismatched");
        assertThatThrownBy(() -> parser.parse(objectMapper.writeValueAsString(mismatchedExecution)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("execution identity");

        JsonNode mismatchedDigest = validBundle.deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) mismatchedDigest.path("case_results").get(0))
            .put("actual_input_digest", "sha256:" + "f".repeat(64));
        assertThatThrownBy(() -> parser.parse(objectMapper.writeValueAsString(mismatchedDigest)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("input digest");

        JsonNode invalidStatus = validBundle.deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) invalidStatus.path("runtime_metrics").get(0))
            .put("provider_status", "UNKNOWN");
        assertThatThrownBy(() -> parser.parse(objectMapper.writeValueAsString(invalidStatus)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("JSON Schema");

        JsonNode missingProvenance = validBundle.deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) missingProvenance.path("execution_config"))
            .putNull("dataset_digest");
        assertThatThrownBy(() -> parser.parse(objectMapper.writeValueAsString(missingProvenance)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("JSON Schema");
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

    private String submitEvaluation(String suffix, int profileIndex) throws Exception {
        var profile = modelProfiles.profiles().get(profileIndex);
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
