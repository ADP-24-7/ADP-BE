package com.adp.gateway.digitalasset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Stream;

import com.adp.gateway.digitalasset.infrastructure.FakeDigitalAssetPlatformStateStore;
import com.adp.gateway.recovery.application.ExternalInteractionRecoveryService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
    "adp.local-fixtures.enabled=true",
    "adp.mock-runtime.enabled=true"
})
@AutoConfigureMockMvc
class DigitalAssetLocalProductE2ETests {
    private static final List<String> FIXTURES = List.of(
        "golden_pass.json",
        "block_amount.json",
        "block_destination.json",
        "execution_failed.json",
        "sent_unknown_recovered.json",
        "duplicate_request.json"
    );

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private ExternalInteractionRecoveryService recoveryService;

    @Autowired
    private FakeDigitalAssetPlatformStateStore platformStateStore;

    @TestFactory
    Stream<DynamicTest> executesDaOwnedSixCaseContractThroughRuntime() {
        Path root = daRoot().resolve("03_digital_asset/artifacts/local_product_e2e_v1");
        assertThat(root).as("DA PR #31 fixture directory").isDirectory();
        return FIXTURES.stream().map(name -> DynamicTest.dynamicTest(name, () -> execute(root.resolve(name))));
    }

    private void execute(Path fixturePath) throws Exception {
        JsonNode fixture = objectMapper.readTree(Files.readString(fixturePath));
        JsonNode request = fixture.path("requested_transaction");
        JsonNode first = submit(request);
        String executionId = first.path("executionId").asText();
        assertThat(executionId).isNotBlank();

        int submissions = fixture.path("submission_count").asInt();
        if (submissions == 2) {
            JsonNode replay = submit(request);
            assertThat(replay.path("executionId").asText()).isEqualTo(executionId);
            assertThat(replay.path("replayed").asBoolean()).isTrue();
        }

        if (fixture.path("expected_reconciliation").path("required").asBoolean()) {
            for (int attempt = 0; attempt < 50 && !reconciled(executionId); attempt++) {
                assertThat(recoveryService.processNext("da-p0-local-product-e2e")).isTrue();
            }
            assertThat(reconciled(executionId)).isTrue();
        }

        String expectedFinalState = fixture.path("expected_final_state").asText();
        JsonNode trace = trace(executionId);
        assertThat(trace.path("status").asText()).isEqualTo(expectedFinalState);
        assertThat(trace.path("digitalAssetRuntimeSnapshot").isObject()).isTrue();

        String expectedDecision = fixture.path("expected_pre_execution_decision").asText();
        if ("PASS".equals(expectedDecision)) {
            assertThat(trace.path("digitalAssetPreExecutionGuard").path("status").asText())
                .isEqualTo("PASSED");
        } else {
            assertThat(trace.path("digitalAssetPreExecutionGuard").isNull()).isTrue();
        }
        assertExecutionContract(executionId, fixture, expectedFinalState);
        assertExternalEffects(executionId,
            fixture.path("expected_reconciliation").path("expected_external_effect_count").asInt());
    }

    private JsonNode submit(JsonNode request) throws Exception {
        JsonNode headers = request.path("headers");
        String response = mockMvc.perform(post(request.path("path").asText())
                .header("X-Request-Id", headers.path("X-Request-Id").asText())
                .header("X-Trace-Id", headers.path("X-Trace-Id").asText())
                .header("X-ADP-Request-Timestamp", OffsetDateTime.now().toString())
                .header("X-ADP-API-Key", "local-dev-api-key")
                .contentType("application/json")
                .content(objectMapper.writeValueAsBytes(request.path("body"))))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response);
    }

    private JsonNode trace(String executionId) throws Exception {
        String response = mockMvc.perform(get("/v1/runtime/executions/{executionId}", executionId)
                .header("X-ADP-API-Key", "local-dev-api-key"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response);
    }

    private void assertExecutionContract(String executionId, JsonNode fixture, String expectedStatus) {
        ExecutionContract row = jdbcClient.sql("""
                select re.status, re.institution_id, re.workload_id, re.purpose_code, re.idempotency_key,
                       re.policy_version, pe.reason_codes,
                       (select count(*) from runtime.digital_asset_post_execution_evidence p
                        where p.execution_id = re.execution_id) as post_evidence_count,
                       (select count(*) from runtime.external_interaction_recovery r
                        where r.execution_id = re.execution_id) as recovery_count
                from runtime.runtime_execution re
                join runtime.execution_pack_policy_evaluation pe on pe.execution_id = re.execution_id
                where re.execution_id = :executionId
                """)
            .param("executionId", executionId)
            .query(ExecutionContract.class)
            .single();
        JsonNode body = fixture.path("requested_transaction").path("body");
        assertThat(row.status()).isEqualTo(expectedStatus);
        assertThat(row.institutionId()).isEqualTo(body.path("institutionId").asText());
        assertThat(row.workloadId()).isEqualTo(body.path("workloadId").asText());
        assertThat(row.purposeCode()).isEqualTo(body.path("purposeCode").asText());
        assertThat(row.idempotencyKey()).isEqualTo(body.path("idempotencyKey").asText());
        assertThat(row.policyVersion()).isNotBlank();

        if ("BLOCK".equals(fixture.path("expected_pre_execution_decision").asText())) {
            fixture.path("expected_reason_codes").forEach(reason ->
                assertThat(row.reasonCodes()).contains(reason.asText()));
            assertThat(row.postEvidenceCount()).isZero();
        } else {
            assertThat(row.postEvidenceCount()).isEqualTo(1);
        }
        boolean recoveryExpected = fixture.path("expected_reconciliation").path("required").asBoolean();
        assertThat(row.recoveryCount()).isEqualTo(recoveryExpected ? 1 : 0);
    }

    private void assertExternalEffects(String executionId, int expected) {
        String providerRequestId = jdbcClient.sql("""
                select provider_request_id from runtime.provider_request where execution_id = :executionId
                """)
            .param("executionId", executionId)
            .query(String.class)
            .optional()
            .orElse(null);
        int actual = providerRequestId == null ? 0 : platformStateStore.externalEffectCount(providerRequestId);
        assertThat(actual).isEqualTo(expected);
    }

    private boolean reconciled(String executionId) {
        return jdbcClient.sql("""
                select recovery_status = 'RECONCILED'
                from runtime.external_interaction_recovery where execution_id = :executionId
                """)
            .param("executionId", executionId)
            .query(Boolean.class)
            .optional()
            .orElse(false);
    }

    private Path daRoot() {
        return Path.of(System.getenv().getOrDefault("ADP_DA_ROOT", "../ADP-DA")).toAbsolutePath().normalize();
    }

    private record ExecutionContract(
        String status,
        String institutionId,
        String workloadId,
        String purposeCode,
        String idempotencyKey,
        String policyVersion,
        String reasonCodes,
        int postEvidenceCount,
        int recoveryCount
    ) {
    }
}
