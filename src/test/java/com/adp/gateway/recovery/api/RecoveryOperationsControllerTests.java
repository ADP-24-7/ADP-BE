package com.adp.gateway.recovery.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.adp.gateway.connector.domain.ConnectorResult;
import com.adp.gateway.connector.domain.ConnectorStatus;
import com.adp.gateway.egress.domain.ExecutionPackType;
import com.adp.gateway.recovery.application.ExternalInteractionRecoveryPersistence;
import com.adp.gateway.recovery.application.RecoveryOperationException;
import com.adp.gateway.recovery.application.RecoveryOperationsPersistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
    "adp.local-fixtures.enabled=true",
    "adp.mock-runtime.enabled=true",
    "adp.local-user-auth.enabled=true",
    "adp.recovery.scheduler.enabled=false"
})
@AutoConfigureMockMvc
class RecoveryOperationsControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private ExternalInteractionRecoveryPersistence recoveryPersistence;

    @Autowired
    private RecoveryOperationsPersistence operationsPersistence;

    private final List<String> seededExecutionIds = new ArrayList<>();

    @AfterEach
    void cleanUpRecoveryRows() {
        for (String executionId : seededExecutionIds) {
            jdbcClient.sql("""
                    delete from runtime.recovery_operation_event
                    where execution_id = :executionId
                    """)
                .param("executionId", executionId)
                .update();
            jdbcClient.sql("""
                    delete from runtime.external_interaction_recovery
                    where execution_id = :executionId
                    """)
                .param("executionId", executionId)
                .update();
        }
        seededExecutionIds.clear();
    }

    @Test
    void auditorReadsScopedIncidentWithoutProviderCorrelationKey() throws Exception {
        Seed seed = seedRecovery("auditor");

        String response = mockMvc.perform(get("/api/admin/recovery/incidents/{recoveryId}", seed.recoveryId())
                .header("X-ADP-User-Id", "auditor-local")
                .header("X-ADP-User-Roles", "AUDITOR")
                .param("executionPack", "AI"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.recoveryId").value(seed.recoveryId()))
            .andExpect(jsonPath("$.executionId").value(seed.executionId()))
            .andExpect(jsonPath("$.institutionId").value("institution_local"))
            .andExpect(jsonPath("$.executionPack").value("AI"))
            .andExpect(jsonPath("$.recoveryStatus").value("PENDING"))
            .andReturn().getResponse().getContentAsString();

        assertThat(response).doesNotContain(seed.providerRequestId());
    }

    @Test
    void incidentListAndDetailUseTheSameServerOwnedPackScope() throws Exception {
        Seed seed = seedRecovery("pack-scope");

        mockMvc.perform(get("/api/admin/recovery/incidents")
                .header("X-ADP-User-Id", "auditor-local")
                .header("X-ADP-User-Roles", "AUDITOR")
                .param("executionPack", "AI"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[?(@.recoveryId == '%s')].executionPack"
                .formatted(seed.recoveryId())).value(hasItem("AI")));

        mockMvc.perform(get("/api/admin/recovery/incidents/{recoveryId}", seed.recoveryId())
                .header("X-ADP-User-Id", "auditor-local")
                .header("X-ADP-User-Roles", "AUDITOR")
                .param("executionPack", "DIGITAL_ASSET"))
            .andExpect(status().isNotFound());
    }

    @Test
    void commandCannotCrossTheRequestedPackBoundary() throws Exception {
        Seed seed = seedRecovery("command-pack-scope");

        mockMvc.perform(post("/api/admin/recovery/incidents/{recoveryId}/review", seed.recoveryId())
                .header("X-ADP-User-Id", "privileged-local")
                .header("X-ADP-User-Roles", "PRIVILEGED_OPERATOR")
                .param("executionPack", "DIGITAL_ASSET")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"operationId\":\"op_wrong_pack_" + seed.suffix() + "\"}"))
            .andExpect(status().isNotFound());

        Integer operationCount = jdbcClient.sql("""
                select count(*) from runtime.recovery_operation_event
                where recovery_id = :recoveryId and operation_id = :operationId
                """)
            .param("recoveryId", seed.recoveryId())
            .param("operationId", "op_wrong_pack_" + seed.suffix())
            .query(Integer.class)
            .single();
        assertThat(operationCount).isZero();
    }

    @Test
    void privilegedReconcileCommandIsIdempotentAndDoesNotBlindRetry() throws Exception {
        Seed seed = seedRecovery("command");
        String operationId = "op_" + seed.suffix();

        mockMvc.perform(post("/api/admin/recovery/incidents/{recoveryId}/reconcile", seed.recoveryId())
                .header("X-ADP-User-Id", "privileged-local")
                .header("X-ADP-User-Roles", "PRIVILEGED_OPERATOR")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"operationId\":\"" + operationId + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.outcome").value("SUCCEEDED"))
            .andExpect(jsonPath("$.recoveryStatus").value("RETRY_SCHEDULED"))
            .andExpect(jsonPath("$.reasonCode").value("STATUS_QUERY_UNAVAILABLE"))
            .andExpect(jsonPath("$.replayed").value(false));

        mockMvc.perform(post("/api/admin/recovery/incidents/{recoveryId}/reconcile", seed.recoveryId())
                .header("X-ADP-User-Id", "privileged-local")
                .header("X-ADP-User-Roles", "PRIVILEGED_OPERATOR")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"operationId\":\"" + operationId + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.replayed").value(true));

        Integer eventCount = jdbcClient.sql("""
                select count(*) from runtime.recovery_operation_event
                where recovery_id = :recoveryId and operation_id = :operationId
                """)
            .param("recoveryId", seed.recoveryId())
            .param("operationId", operationId)
            .query(Integer.class)
            .single();
        assertThat(eventCount).isEqualTo(1);
    }

    @Test
    void concurrentPackScopedCommandsReserveOneOperationAndApplyOneRecoveryTransition() throws Exception {
        Seed seed = seedRecovery("concurrent-command");
        String operationId = "op_concurrent_" + seed.suffix();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var request = (java.util.concurrent.Callable<MvcResult>) () -> {
                ready.countDown();
                start.await();
                return mockMvc.perform(post("/api/admin/recovery/incidents/{recoveryId}/reconcile", seed.recoveryId())
                        .header("X-ADP-User-Id", "privileged-local")
                        .header("X-ADP-User-Roles", "PRIVILEGED_OPERATOR")
                        .param("executionPack", "AI")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"operationId\":\"" + operationId + "\"}"))
                    .andReturn();
            };
            Future<MvcResult> first = executor.submit(request);
            Future<MvcResult> second = executor.submit(request);
            ready.await();
            start.countDown();

            assertThat(List.of(first.get().getResponse().getStatus(), second.get().getResponse().getStatus()))
                .contains(200)
                .allMatch(statusCode -> statusCode == 200 || statusCode == 409);
        } finally {
            executor.shutdownNow();
        }

        Integer eventCount = jdbcClient.sql("""
                select count(*) from runtime.recovery_operation_event
                where recovery_id = :recoveryId and operation_id = :operationId
                """)
            .param("recoveryId", seed.recoveryId())
            .param("operationId", operationId)
            .query(Integer.class)
            .single();
        assertThat(eventCount).isEqualTo(1);
        assertThat(recoveryAttempt(seed.recoveryId()))
            .isEqualTo(new RecoveryAttempt("RETRY_SCHEDULED", 1));
    }

    @Test
    void markReviewDoesNotConsumeAttemptAndCanLaterBeReconciled() throws Exception {
        Seed seed = seedRecovery("review-attempt");
        jdbcClient.sql("""
                update runtime.external_interaction_recovery
                set attempt_count = 4, max_attempts = 5
                where recovery_id = :recoveryId
                """)
            .param("recoveryId", seed.recoveryId())
            .update();

        mockMvc.perform(post("/api/admin/recovery/incidents/{recoveryId}/review", seed.recoveryId())
                .header("X-ADP-User-Id", "privileged-local")
                .header("X-ADP-User-Roles", "PRIVILEGED_OPERATOR")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"operationId\":\"op_review_" + seed.suffix() + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.recoveryStatus").value("MANUAL_REVIEW"));

        RecoveryAttempt reviewed = recoveryAttempt(seed.recoveryId());
        assertThat(reviewed.recoveryStatus()).isEqualTo("MANUAL_REVIEW");
        assertThat(reviewed.attemptCount()).isEqualTo(4);

        OffsetDateTime now = OffsetDateTime.now();
        var claimed = recoveryPersistence.claimById(
            seed.recoveryId(), "worker-after-review", "institution_local", Set.of("customer_summary"),
            now, java.time.Duration.ofSeconds(30)
        ).orElseThrow();
        assertThat(claimed.attemptCount()).isEqualTo(5);
        assertThat(recoveryPersistence.reconcile(
            seed.recoveryId(), "worker-after-review",
            new com.adp.gateway.recovery.domain.ExternalStatusQueryResult(
                ConnectorStatus.ACKNOWLEDGED, "a".repeat(64)
            ),
            now.plusSeconds(1)
        )).isTrue();

        assertThat(recoveryAttempt(seed.recoveryId()).recoveryStatus()).isEqualTo("RECONCILED");
        String runtimeStatus = jdbcClient.sql("""
                select status from runtime.runtime_execution where execution_id = :executionId
                """)
            .param("executionId", seed.executionId())
            .query(String.class)
            .single();
        assertThat(runtimeStatus).isEqualTo("EXTERNALLY_RECONCILED");
    }

    @Test
    void operatorCannotIssueRecoveryCommand() throws Exception {
        Seed seed = seedRecovery("forbidden");

        mockMvc.perform(post("/api/admin/recovery/incidents/{recoveryId}/review", seed.recoveryId())
                .header("X-ADP-User-Id", "operator-local")
                .header("X-ADP-User-Roles", "OPERATOR")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"operationId\":\"op_forbidden_" + seed.suffix() + "\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void jdbcReadAlwaysAppliesInstitutionAndWorkloadScope() throws Exception {
        Seed seed = seedRecovery("scope");

        assertThat(operationsPersistence.search(
            "institution_local", Set.of("fraud_detection"), ExecutionPackType.AI, null, 0, 10
        ).items()).noneMatch(item -> item.recoveryId().equals(seed.recoveryId()));
        assertThatThrownBy(() -> operationsPersistence.load(
            seed.recoveryId(), "institution_other", Set.of("customer_summary"), ExecutionPackType.AI
        )).isInstanceOf(RecoveryOperationException.class)
            .hasMessage("RECOVERY_INCIDENT_NOT_FOUND");

        assertThat(operationsPersistence.search(
            "institution_local", Set.of("customer_summary"), ExecutionPackType.DIGITAL_ASSET, null, 0, 10
        ).items()).noneMatch(item -> item.recoveryId().equals(seed.recoveryId()));
        assertThatThrownBy(() -> operationsPersistence.load(
            seed.recoveryId(), "institution_local", Set.of("customer_summary"),
            ExecutionPackType.DIGITAL_ASSET
        )).isInstanceOf(RecoveryOperationException.class)
            .hasMessage("RECOVERY_INCIDENT_NOT_FOUND");
    }

    private Seed seedRecovery(String label) throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String runtimeResponse = mockMvc.perform(post("/v1/runtime/executions")
                .header("X-Request-Id", "req_recovery_ops_" + suffix)
                .header("X-Trace-Id", "trace_recovery_ops_" + suffix)
                .header("X-ADP-API-Key", "local-dev-api-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request("idem_recovery_ops_" + label + "_" + suffix)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(runtimeResponse);
        String executionId = json.path("executionId").asText();
        seededExecutionIds.add(executionId);
        ConnectorSeed connector = jdbcClient.sql("""
                select re.connector_execution_id, ce.connector_id, re.outbound_payload_id,
                       re.outbound_candidate_digest, re.provider_request_id
                from runtime.runtime_execution re
                join runtime.connector_execution ce on ce.connector_execution_id = re.connector_execution_id
                where re.execution_id = :executionId
                """)
            .param("executionId", executionId)
            .query(ConnectorSeed.class)
            .single();
        jdbcClient.sql("""
                update runtime.runtime_execution
                set status = 'EGRESSING', connector_status = 'SENT_UNKNOWN',
                    idempotency_expires_at = null
                where execution_id = :executionId
                """)
            .param("executionId", executionId)
            .update();
        jdbcClient.sql("""
                update runtime.connector_execution set status = 'SENT_UNKNOWN'
                where connector_execution_id = :connectorExecutionId
                """)
            .param("connectorExecutionId", connector.connectorExecutionId())
            .update();
        recoveryPersistence.scheduleUnknown(executionId, new ConnectorResult(
            connector.connectorExecutionId(), connector.connectorId(), ConnectorStatus.SENT_UNKNOWN,
            connector.outboundPayloadId(), connector.outboundCandidateDigest(), null, null
        ), OffsetDateTime.now());
        String recoveryId = jdbcClient.sql("""
                select recovery_id from runtime.external_interaction_recovery
                where execution_id = :executionId
                """)
            .param("executionId", executionId)
            .query(String.class)
            .single();
        return new Seed(recoveryId, executionId, connector.providerRequestId(), suffix);
    }

    private RecoveryAttempt recoveryAttempt(String recoveryId) {
        return jdbcClient.sql("""
                select recovery_status, attempt_count
                from runtime.external_interaction_recovery
                where recovery_id = :recoveryId
                """)
            .param("recoveryId", recoveryId)
            .query(RecoveryAttempt.class)
            .single();
    }

    private String request(String idempotencyKey) {
        return """
            {
              "institutionId":"institution_local",
              "approvalReference":"approval_ai_customer_support_v1",
              "workloadId":"customer_summary",
              "purposeCode":"CUSTOMER_SUPPORT",
              "subjectScope":"customer:customer-100",
              "destinationProfileId":"dest_internal_provider_project_provisional",
              "idempotencyKey":"%s",
              "processingContexts":["AI_USE"],
              "input":{"prompt":"safe recovery operations question"}
            }
            """.formatted(idempotencyKey);
    }

    private record ConnectorSeed(
        String connectorExecutionId,
        String connectorId,
        String outboundPayloadId,
        String outboundCandidateDigest,
        String providerRequestId
    ) {
    }

    private record Seed(String recoveryId, String executionId, String providerRequestId, String suffix) {
    }

    private record RecoveryAttempt(String recoveryStatus, int attemptCount) {
    }
}
