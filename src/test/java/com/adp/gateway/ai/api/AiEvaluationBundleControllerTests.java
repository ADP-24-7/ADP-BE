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
import com.adp.gateway.ai.application.AiEvaluationPrompt;
import com.adp.gateway.ai.application.AiEvaluationRunCatalog;
import com.adp.gateway.ai.application.AiModelProfileCatalog;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;

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

    @Autowired
    private AiEvaluationRunCatalog evaluationRuns;

    @Autowired
    private JdbcClient jdbcClient;

    @org.junit.jupiter.api.BeforeEach
    void freezeEvaluationContract() throws Exception {
        mockMvc.perform(post("/api/admin/ai/evaluation-runs/{runId}/contract/freeze",
                AiEvaluationRunCatalog.BASELINE_RUN_ID)
                .header("X-ADP-User-Id", "privileged-local")
                .header("X-ADP-User-Roles", "PRIVILEGED_OPERATOR"))
            .andExpect(status().isOk());
    }

    @Test
    void privilegedOperatorExportsDaConsumableEvaluationBundle() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        var executionIds = new java.util.ArrayList<String>();
        for (int index = 0; index < modelProfiles.profiles().size(); index++) {
            executionIds.add(submitEvaluation(suffix + "_" + index, index));
        }

        readiness("PRIVILEGED_OPERATOR")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("READY"))
            .andExpect(jsonPath("$.bundle_available").value(true))
            .andExpect(jsonPath("$.expected_execution_count").value(3))
            .andExpect(jsonPath("$.stored_execution_count").isNumber())
            .andExpect(jsonPath("$.observed_execution_count").value(3))
            .andExpect(jsonPath("$.complete_evidence_count").value(3))
            .andExpect(jsonPath("$.missing_execution_count").value(0))
            .andExpect(jsonPath("$.case_models.length()").value(3));

        String firstResponse = export("PRIVILEGED_OPERATOR")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.manifest.schema_version").value("adp-ai-evaluation-bundle/v2"))
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
        String recomputedDigest = canonicalizer.digest(digestContent(first));
        assertThat(first.path("manifest").path("content_digest").asText()).isEqualTo(recomputedDigest);
        assertDaParserRejectsInconsistentIdentity(first);
        executionIds.forEach(executionId -> {
            assertThat(first.path("case_results").toString()).contains(executionId);
            assertThat(first.path("runtime_metrics").toString()).contains(executionId);
            assertThat(first.path("trace_index").toString()).contains(executionId);
        });
        assertOnlyFrozenPublicMetadataContainsPromptAndSubject(first);
        ObjectNode leakedPrompt = first.deepCopy();
        ((ObjectNode) leakedPrompt.path("case_results").get(0))
            .put("raw_prompt", AiEvaluationPrompt.TEXT);
        assertThatThrownBy(() -> assertOnlyFrozenPublicMetadataContainsPromptAndSubject(leakedPrompt))
            .isInstanceOf(AssertionError.class);
        ObjectNode leakedSubject = first.deepCopy();
        ((ObjectNode) leakedSubject.path("contract_evidence")).put("raw_subject", "customer-100");
        assertThatThrownBy(() -> assertOnlyFrozenPublicMetadataContainsPromptAndSubject(leakedSubject))
            .isInstanceOf(AssertionError.class);

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

    private void assertOnlyFrozenPublicMetadataContainsPromptAndSubject(JsonNode bundle) throws Exception {
        ObjectNode evidence = bundle.deepCopy();
        JsonNode fixed = evidence.path("contract_evidence").path("snapshot").path("fixed_conditions");
        JsonNode prompt = fixed.path("prompt_snapshot");
        JsonNode cases = fixed.path("cases");
        assertThat(prompt).isEqualTo(objectMapper.valueToTree(AiEvaluationPrompt.snapshot()));
        assertThat(cases).isEqualTo(objectMapper.valueToTree(evaluationRuns
            .find(AiEvaluationRunCatalog.BASELINE_RUN_ID).orElseThrow().cases().values().stream()
            .sorted(java.util.Comparator.comparing(com.adp.gateway.ai.domain.AiEvaluationCaseDefinition::caseId))
            .toList()));

        // V2 freezes these exact public metadata values; raw execution data is still forbidden.
        ((ObjectNode) prompt).remove("input_prompt");
        cases.forEach(evaluationCase -> ((ObjectNode) evaluationCase).remove("datasetRowRef"));
        assertThat(objectMapper.writeValueAsString(evidence))
            .doesNotContain("승인된 고객 정보를 간단히 요약하세요")
            .doesNotContain("customer-100")
            .doesNotContain("local-dev-api-key")
            .doesNotContain("req_eval_bundle_")
            .doesNotContain("trace_eval_bundle_");
    }

    private void assertDaParserRejectsInconsistentIdentity(JsonNode validBundle) throws Exception {
        var parser = new DaEvaluationBundleParserFixture(objectMapper);

        JsonNode mismatchedExecution = validBundle.deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) mismatchedExecution.path("runtime_metrics").get(0))
            .put("execution_id", "exec-mismatched");
        refreshContentDigest(mismatchedExecution);
        assertThatThrownBy(() -> parser.parse(objectMapper.writeValueAsString(mismatchedExecution)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("execution identity");

        JsonNode mismatchedDigest = validBundle.deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) mismatchedDigest.path("case_results").get(0))
            .put("actual_input_digest", "sha256:" + "f".repeat(64));
        refreshContentDigest(mismatchedDigest);
        assertThatThrownBy(() -> parser.parse(objectMapper.writeValueAsString(mismatchedDigest)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("input digest");

        JsonNode changedPayload = validBundle.deepCopy();
        var changedMetric = (com.fasterxml.jackson.databind.node.ObjectNode)
            changedPayload.path("runtime_metrics").get(0);
        changedMetric.put(
            "initial_runtime_latency_millis",
            changedMetric.path("initial_runtime_latency_millis").asLong() + 1
        );
        assertThatThrownBy(() -> parser.parse(objectMapper.writeValueAsString(changedPayload)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("content digest");

        JsonNode incompleteMatrix = validBundle.deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) incompleteMatrix.path("case_results").get(0))
            .put("eval_case_id", "second-case");
        ((com.fasterxml.jackson.databind.node.ObjectNode) incompleteMatrix.path("runtime_metrics").get(0))
            .put("eval_case_id", "second-case");
        ((com.fasterxml.jackson.databind.node.ObjectNode) incompleteMatrix.path("manifest"))
            .put("case_count", 2);
        refreshContentDigest(incompleteMatrix);
        assertThatThrownBy(() -> parser.parse(objectMapper.writeValueAsString(incompleteMatrix)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Cartesian product");

        JsonNode inconsistentSummary = validBundle.deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) inconsistentSummary.path("failure_summary"))
            .put("failed", 1);
        refreshContentDigest(inconsistentSummary);
        assertThatThrownBy(() -> parser.parse(objectMapper.writeValueAsString(inconsistentSummary)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("failure summary");

        JsonNode invalidStatus = validBundle.deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) invalidStatus.path("runtime_metrics").get(0))
            .put("provider_status", "UNKNOWN");
        assertThatThrownBy(() -> parser.parse(objectMapper.writeValueAsString(invalidStatus)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("JSON Schema");

        JsonNode invalidTiming = validBundle.deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) invalidTiming.path("runtime_metrics").get(0))
            .put("measurement_type", "NOT_ATTEMPTED");
        assertThatThrownBy(() -> parser.parse(objectMapper.writeValueAsString(invalidTiming)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("JSON Schema");

        JsonNode invalidTokenUsage = validBundle.deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) invalidTokenUsage.path("runtime_metrics").get(0))
            .put("token_usage_status", "COMPLETE");
        assertThatThrownBy(() -> parser.parse(objectMapper.writeValueAsString(invalidTokenUsage)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("JSON Schema");

        JsonNode missingProvenance = validBundle.deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) missingProvenance.path("execution_config"))
            .putNull("dataset_digest");
        assertThatThrownBy(() -> parser.parse(objectMapper.writeValueAsString(missingProvenance)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("JSON Schema");
    }

    private Map<String, Object> digestContent(JsonNode bundle) {
        return Map.of(
            "schema_version", bundle.path("manifest").path("schema_version").asText(),
            "contract_evidence", objectMapper.convertValue(bundle.path("contract_evidence"), Object.class),
            "execution_config", objectMapper.convertValue(bundle.path("execution_config"), Object.class),
            "case_results", objectMapper.convertValue(bundle.path("case_results"), Object.class),
            "runtime_metrics", objectMapper.convertValue(bundle.path("runtime_metrics"), Object.class),
            "failure_summary", objectMapper.convertValue(bundle.path("failure_summary"), Object.class),
            "trace_index", objectMapper.convertValue(bundle.path("trace_index"), Object.class)
        );
    }

    private void refreshContentDigest(JsonNode bundle) {
        ((com.fasterxml.jackson.databind.node.ObjectNode) bundle.path("manifest"))
            .put("content_digest", canonicalizer.digest(digestContent(bundle)));
    }

    @Test
    void operatorCannotExportEvaluationBundle() throws Exception {
        export("OPERATOR").andExpect(status().isForbidden());
        readiness("OPERATOR").andExpect(status().isForbidden());
    }

    @Test
    void inaccessibleEvaluationRunReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/admin/ai/evaluation-runs/{runId}/bundle", "missing-run")
                .header("X-ADP-User-Id", "privileged-local")
                .header("X-ADP-User-Roles", "PRIVILEGED_OPERATOR"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.reasonCode").value("AI_EVALUATION_BUNDLE_NOT_FOUND"));
    }

    @Test
    void exportsPrivacySafeCalibrationEvidenceForTheSelectedCaseModelMatrix() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        var executionIds = new java.util.ArrayList<String>();
        for (int index = 0; index < modelProfiles.profiles().size(); index++) {
            executionIds.add(submitEvaluation("calibration_" + suffix + "_" + index, index));
        }
        addReflectionFinding(executionIds.getFirst(), true);

        String response = mockMvc.perform(get(
                "/api/admin/ai/evaluation-runs/{runId}/calibration-evidence",
                AiEvaluationRunCatalog.BASELINE_RUN_ID
            )
            .header("X-ADP-User-Id", "bundle-exporter")
            .header("X-ADP-User-Roles", "PRIVILEGED_OPERATOR"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.manifest.schema_version").value("adp-ai-calibration-evidence/v1"))
            .andExpect(jsonPath("$.manifest.content_digest")
                .value(org.hamcrest.Matchers.matchesPattern("sha256:[0-9a-f]{64}")))
            .andExpect(jsonPath("$.manifest.execution_count").value(3))
            .andExpect(jsonPath("$.calibration_ready").value(true))
            .andExpect(jsonPath("$.readiness_reason_codes.length()").value(0))
            .andExpect(jsonPath("$..raw_value").doesNotExist())
            .andExpect(jsonPath("$..outbound_field_path").doesNotExist())
            .andReturn().getResponse().getContentAsString();
        JsonNode reflected = java.util.stream.StreamSupport.stream(
                objectMapper.readTree(response).path("executions").spliterator(), false
            )
            .filter(execution -> execution.path("finding_count").asInt() == 1)
            .findFirst()
            .orElseThrow();
        JsonNode group = reflected.path("finding_groups").get(0);
        assertThat(group.path("finding_type").asText()).isEqualTo("RAW_VALUE_REFLECTION");
        assertThat(group.path("source_data_class").asText()).isEqualTo("TRANSACTION_IDENTIFIER");
        assertThat(group.path("transform_strategy").asText()).isEqualTo("HMAC_PSEUDO");
        assertThat(group.path("field_treatment").asText()).isEqualTo("TRANSFORMED");
        assertThat(group.path("evidence_digests").get(0).asText()).isEqualTo("b".repeat(64));
        assertCalibrationContract(response);
    }

    @Test
    void marksLegacyReflectionEvidenceAsNotCalibrationReady() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        var executionIds = new java.util.ArrayList<String>();
        for (int index = 0; index < modelProfiles.profiles().size(); index++) {
            executionIds.add(submitEvaluation("legacy_calibration_" + suffix + "_" + index, index));
        }
        addReflectionFinding(executionIds.getFirst(), false);

        String response = mockMvc.perform(get(
                "/api/admin/ai/evaluation-runs/{runId}/calibration-evidence",
                AiEvaluationRunCatalog.BASELINE_RUN_ID
            )
            .header("X-ADP-User-Id", "bundle-exporter")
            .header("X-ADP-User-Roles", "PRIVILEGED_OPERATOR"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.calibration_ready").value(false))
            .andExpect(jsonPath("$.readiness_reason_codes[0]").value("REFLECTION_METADATA_MISSING"))
            .andReturn().getResponse().getContentAsString();
        assertThat(java.util.stream.StreamSupport.stream(
                objectMapper.readTree(response).path("executions").spliterator(), false
            )
            .mapToInt(execution -> execution.path("missing_reflection_metadata_count").asInt())
            .sum()).isEqualTo(1);
    }

    @Test
    void operatorCannotExportCalibrationEvidence() throws Exception {
        mockMvc.perform(get(
                "/api/admin/ai/evaluation-runs/{runId}/calibration-evidence",
                AiEvaluationRunCatalog.BASELINE_RUN_ID
            )
            .header("X-ADP-User-Id", "operator-local")
            .header("X-ADP-User-Roles", "OPERATOR"))
            .andExpect(status().isForbidden());
    }

    private void addReflectionFinding(String executionId, boolean withMetadata) {
        String connectorExecutionId = jdbcClient.sql("""
                select connector_execution_id from runtime.response_guard_result
                where execution_id = :executionId
                """)
            .param("executionId", executionId)
            .query(String.class)
            .single();
        jdbcClient.sql("""
            insert into runtime.response_sensitive_finding (
                connector_execution_id, execution_id, finding_type, location,
                start_offset, end_offset, detector_version, evidence_digest,
                source_data_class, transform_strategy, field_treatment,
                outbound_field_path_digest, created_at
            ) values (
                :connectorExecutionId, :executionId, 'RAW_VALUE_REFLECTION', '$.response',
                1, 8, 'ai-response-regex-v2', :evidenceDigest,
                :sourceDataClass, :transformStrategy, :fieldTreatment,
                :fieldPathDigest, current_timestamp
            )
            """)
            .param("connectorExecutionId", connectorExecutionId)
            .param("executionId", executionId)
            .param("evidenceDigest", "b".repeat(64))
            .param("sourceDataClass", withMetadata ? "TRANSACTION_IDENTIFIER" : null)
            .param("transformStrategy", withMetadata ? "HMAC_PSEUDO" : null)
            .param("fieldTreatment", withMetadata ? "TRANSFORMED" : null)
            .param("fieldPathDigest", withMetadata ? "c".repeat(64) : null)
            .update();
        jdbcClient.sql("""
            update runtime.response_guard_result
            set status = 'REJECTED', leakage_detected = true,
                reason_codes = 'RESPONSE_SENSITIVE_DATA_DETECTED', finding_count = 1
            where execution_id = :executionId
            """)
            .param("executionId", executionId)
            .update();
        jdbcClient.sql("""
            update runtime.runtime_execution
            set response_guard_status = 'REJECTED', controlled_delivery_status = 'WITHHELD',
                response_guard_reason_codes = 'RESPONSE_SENSITIVE_DATA_DETECTED'
            where execution_id = :executionId
            """)
            .param("executionId", executionId)
            .update();
    }

    private void assertCalibrationContract(String response) throws Exception {
        String schemaJson = java.nio.file.Files.readString(
            java.nio.file.Path.of("docs/contracts/ai-calibration-evidence.schema.json")
        );
        var schema = com.networknt.schema.SchemaRegistry
            .withDefaultDialect(com.networknt.schema.SpecificationVersion.DRAFT_2020_12)
            .getSchema(schemaJson, com.networknt.schema.InputFormat.JSON);
        assertThat(schema.validate(
            response,
            com.networknt.schema.InputFormat.JSON,
            context -> context.executionConfig(config -> config.formatAssertionsEnabled(true))
        )).isEmpty();

        JsonNode root = objectMapper.readTree(response);
        Map<String, Object> content = new java.util.TreeMap<>();
        content.put("schema_version", root.path("manifest").path("schema_version").asText());
        content.put("evaluation_run_id", root.path("manifest").path("evaluation_run_id").asText());
        content.put("evaluation_run_version", root.path("manifest").path("evaluation_run_version").asText());
        content.put("calibration_ready", root.path("calibration_ready").asBoolean());
        content.put("readiness_reason_codes", objectMapper.convertValue(
            root.path("readiness_reason_codes"), Object.class
        ));
        content.put("executions", objectMapper.convertValue(root.path("executions"), Object.class));
        assertThat(root.path("manifest").path("content_digest").asText())
            .isEqualTo(canonicalizer.digest(content));
    }

    private org.springframework.test.web.servlet.ResultActions export(String role) throws Exception {
        return mockMvc.perform(get(
                "/api/admin/ai/evaluation-runs/{runId}/bundle", AiEvaluationRunCatalog.BASELINE_RUN_ID
            )
            .header("X-ADP-User-Id", "bundle-exporter")
            .header("X-ADP-User-Roles", role));
    }

    private org.springframework.test.web.servlet.ResultActions readiness(String role) throws Exception {
        return mockMvc.perform(get(
                "/api/admin/ai/evaluation-runs/{runId}/readiness", AiEvaluationRunCatalog.BASELINE_RUN_ID
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
