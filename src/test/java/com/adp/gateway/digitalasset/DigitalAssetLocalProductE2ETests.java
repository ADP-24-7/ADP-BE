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
    private static final List<String> PRE_EXECUTION_CONTROLS = List.of(
        "APPROVED_VS_REQUESTED_MATCH",
        "REQUIRED_OUTBOUND_FIELD_PRESENCE",
        "REQUIRED_EXACT_PRESERVATION",
        "TRANSFORM_FIELD_SEPARATION",
        "DESTINATION_SPECIFIC_PAYLOAD",
        "TRACE_BINDING"
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
        JsonNode initialTrace = trace(executionId);
        String pinnedSnapshotDigest = initialTrace.path("digitalAssetRuntimeSnapshot")
            .path("snapshotDigest").asText();
        assertThat(pinnedSnapshotDigest).matches("sha256:[0-9a-f]{64}");

        int submissions = fixture.path("submission_count").asInt();
        if (submissions == 2) {
            JsonNode replay = submit(request);
            assertThat(replay.path("executionId").asText()).isEqualTo(executionId);
            assertThat(replay.path("replayed").asBoolean()).isTrue();
            assertSingleIdempotentExecution(request);
        }

        if (fixture.path("expected_reconciliation").path("required").asBoolean()) {
            for (int attempt = 0; attempt < 50 && !reconciled(executionId); attempt++) {
                assertThat(recoveryService.processNext("da-p0-local-product-e2e"))
                    .as("recovery attempt=%s, state=%s", attempt + 1, recoveryDiagnostic(executionId))
                    .isTrue();
            }
            assertThat(reconciled(executionId))
                .as("recovery did not converge: %s", recoveryDiagnostic(executionId))
                .isTrue();
        }

        String expectedFinalState = fixture.path("expected_final_state").asText();
        JsonNode trace = trace(executionId);
        assertThat(trace.path("status").asText()).isEqualTo(expectedFinalState);
        assertThat(trace.path("executionId").asText()).isEqualTo(executionId);
        assertThat(trace.path("workloadId").asText())
            .isEqualTo(request.path("body").path("workloadId").asText());
        assertThat(trace.path("purposeCode").asText())
            .isEqualTo(request.path("body").path("purposeCode").asText());
        assertThat(trace.path("traceId").asText()).isNotBlank();
        assertThat(trace.path("digitalAssetRuntimeSnapshot").isObject()).isTrue();
        assertThat(trace.path("digitalAssetRuntimeSnapshot").path("snapshotDigest").asText())
            .isEqualTo(pinnedSnapshotDigest);

        String expectedDecision = fixture.path("expected_pre_execution_decision").asText();
        if ("PASS".equals(expectedDecision)) {
            assertPassedPreExecutionGuard(trace);
            assertPostExecutionEvidence(trace, fixture.path("fixture_id").asText());
        } else {
            assertThat(trace.path("digitalAssetPreExecutionGuard").isNull()).isTrue();
            assertThat(trace.path("digitalAssetPostExecutionEvidence").isNull()).isTrue();
        }
        assertExecutionContract(executionId, fixture, expectedFinalState);
        assertDigitalAssetOutcome(executionId, fixture.path("fixture_id").asText());
        if (fixture.path("expected_reconciliation").path("required").asBoolean()) {
            assertRecoveryEvidence(executionId);
        }
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
        String response = mockMvc.perform(get("/v1/runtime/executions/{executionId}/trace", executionId)
                .header("X-ADP-API-Key", "local-dev-api-key"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response);
    }

    private void assertPassedPreExecutionGuard(JsonNode trace) {
        JsonNode guard = trace.path("digitalAssetPreExecutionGuard");
        assertThat(guard.path("status").asText()).isEqualTo("PASSED");
        assertThat(guard.path("snapshotId").asText())
            .isEqualTo(trace.path("digitalAssetRuntimeSnapshot").path("snapshotId").asText());
        JsonNode controls = guard.path("controlResults");
        assertThat(controls.size()).isEqualTo(6);
        PRE_EXECUTION_CONTROLS.forEach(control ->
            assertThat(controls.path(control).asText()).as(control).isEqualTo("PASSED"));
        assertThat(guard.path("reasonCodes").isEmpty()).isTrue();
        assertThat(guard.path("outboundPayloadDigest").asText()).matches("[0-9a-f]{64}");
        assertThat(guard.path("providerPayloadDigest").asText()).matches("[0-9a-f]{64}");
    }

    private void assertPostExecutionEvidence(JsonNode trace, String fixtureId) {
        JsonNode evidence = trace.path("digitalAssetPostExecutionEvidence");
        assertThat(evidence.path("evidenceSourceType").asText())
            .isEqualTo("INDEPENDENT_EXTERNAL");
        if ("EXECUTION_FAILED".equals(fixtureId)) {
            assertThat(evidence.path("status").asText()).isEqualTo("FAILED");
            assertThat(evidence.path("receiptStatus").asText()).isEqualTo("FAILED");
        } else {
            assertThat(evidence.path("status").asText()).isEqualTo("VERIFIED");
            assertThat(evidence.path("receiptStatus").asText()).isEqualTo("SUCCESS");
        }
        assertThat(evidence.path("finalityStatus").asText()).isEqualTo("FINALIZED");
        assertThat(evidence.path("transactionDetailDigest").asText()).matches("[0-9a-f]{64}");
        assertThat(evidence.path("receiptFinalityDigest").asText()).matches("[0-9a-f]{64}");
        assertThat(evidence.path("exactAmountDigest").asText()).matches("[0-9a-f]{64}");
    }

    private void assertExecutionContract(String executionId, JsonNode fixture, String expectedStatus) {
        ExecutionContract row = jdbcClient.sql("""
                select re.status, re.institution_id, re.workload_id, re.purpose_code, re.idempotency_key,
                       re.request_hash, re.policy_version, pe.reason_codes,
                       (select count(*) from runtime.digital_asset_runtime_snapshot s
                        where s.execution_id = re.execution_id) as snapshot_count,
                       (select count(*) from runtime.digital_asset_pre_execution_guard g
                        where g.execution_id = re.execution_id) as pre_guard_count,
                       (select count(*) from runtime.provider_request pr
                        where pr.execution_id = re.execution_id) as provider_request_count,
                       (select count(*) from runtime.connector_execution ce
                        where ce.execution_id = re.execution_id) as connector_count,
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
        assertThat(row.requestHash()).matches("[0-9a-f]{64}");
        assertThat(row.policyVersion()).isNotBlank();
        assertThat(row.snapshotCount()).isEqualTo(1);

        if ("BLOCK".equals(fixture.path("expected_pre_execution_decision").asText())) {
            fixture.path("expected_reason_codes").forEach(reason ->
                assertThat(row.reasonCodes()).contains(reason.asText()));
            assertThat(row.preGuardCount()).isZero();
            assertThat(row.providerRequestCount()).isZero();
            assertThat(row.connectorCount()).isZero();
            assertThat(row.postEvidenceCount()).isZero();
        } else {
            assertThat(row.preGuardCount()).isEqualTo(1);
            assertThat(row.providerRequestCount()).isEqualTo(1);
            assertThat(row.connectorCount()).isEqualTo(1);
            assertThat(row.postEvidenceCount()).isEqualTo(1);
        }
        boolean recoveryExpected = fixture.path("expected_reconciliation").path("required").asBoolean();
        assertThat(row.recoveryCount()).isEqualTo(recoveryExpected ? 1 : 0);
    }

    private void assertSingleIdempotentExecution(JsonNode request) {
        JsonNode body = request.path("body");
        Integer count = jdbcClient.sql("""
                select count(*) from runtime.runtime_execution
                where institution_id = :institutionId
                  and workload_id = :workloadId
                  and idempotency_key = :idempotencyKey
                  and idempotency_archived_at is null
                """)
            .param("institutionId", body.path("institutionId").asText())
            .param("workloadId", body.path("workloadId").asText())
            .param("idempotencyKey", body.path("idempotencyKey").asText())
            .query(Integer.class)
            .single();
        assertThat(count).isEqualTo(1);
    }

    private void assertDigitalAssetOutcome(String executionId, String fixtureId) {
        if ("BLOCK_AMOUNT".equals(fixtureId) || "BLOCK_DESTINATION".equals(fixtureId)) {
            Integer count = jdbcClient.sql("""
                    select count(*) from runtime.digital_asset_transaction
                    where execution_id = :executionId
                    """)
                .param("executionId", executionId)
                .query(Integer.class)
                .single();
            assertThat(count).isZero();
            return;
        }
        DigitalAssetOutcome outcome = jdbcClient.sql("""
                select settlement_status, reconciliation_result
                from runtime.digital_asset_transaction where execution_id = :executionId
                """)
            .param("executionId", executionId)
            .query(DigitalAssetOutcome.class)
            .single();
        switch (fixtureId) {
            case "EXECUTION_FAILED" -> {
                assertThat(outcome.settlementStatus()).isEqualTo("FAILED");
                assertThat(outcome.reconciliationResult()).isEqualTo("WAIT");
            }
            case "SENT_UNKNOWN_RECOVERED" -> {
                assertThat(outcome.settlementStatus()).isEqualTo("SETTLED");
                assertThat(outcome.reconciliationResult()).isEqualTo("RECOVERED");
            }
            default -> {
                assertThat(outcome.settlementStatus()).isEqualTo("SETTLED");
                assertThat(outcome.reconciliationResult()).isEqualTo("MATCH");
            }
        }
    }

    private void assertRecoveryEvidence(String executionId) {
        RecoveryEvidence evidence = jdbcClient.sql("""
                select recovery_status, retry_disposition, last_observed_external_status,
                       status_query_evidence_digest
                from runtime.external_interaction_recovery where execution_id = :executionId
                """)
            .param("executionId", executionId)
            .query(RecoveryEvidence.class)
            .single();
        assertThat(evidence.recoveryStatus()).isEqualTo("RECONCILED");
        assertThat(evidence.retryDisposition()).isEqualTo("RECONCILE_FIRST");
        assertThat(evidence.lastObservedExternalStatus()).isEqualTo("ACKNOWLEDGED");
        assertThat(evidence.statusQueryEvidenceDigest()).matches("[0-9a-f]{64}");
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

    private RecoveryDiagnostic recoveryDiagnostic(String executionId) {
        return jdbcClient.sql("""
                select recovery_status, retry_disposition, next_attempt_at, last_error_code
                from runtime.external_interaction_recovery where execution_id = :executionId
                """)
            .param("executionId", executionId)
            .query(RecoveryDiagnostic.class)
            .optional()
            .orElse(null);
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
        String requestHash,
        String policyVersion,
        String reasonCodes,
        int snapshotCount,
        int preGuardCount,
        int providerRequestCount,
        int connectorCount,
        int postEvidenceCount,
        int recoveryCount
    ) {
    }

    private record RecoveryDiagnostic(
        String recoveryStatus,
        String retryDisposition,
        OffsetDateTime nextAttemptAt,
        String lastErrorCode
    ) {
    }

    private record DigitalAssetOutcome(String settlementStatus, String reconciliationResult) {
    }

    private record RecoveryEvidence(
        String recoveryStatus,
        String retryDisposition,
        String lastObservedExternalStatus,
        String statusQueryEvidenceDigest
    ) {
    }
}
