package com.adp.gateway.observability.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

import com.adp.gateway.observability.application.OperationsMonitoringPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
    "adp.local-user-auth.enabled=true",
    "adp.recovery.scheduler.enabled=false"
})
@AutoConfigureMockMvc
class OperationsMonitoringControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private OperationsMonitoringPort port;

    private String suffix;
    private String workload;
    private String artifactId;
    private String executionId;
    private String recoveryId;
    private String connectorExecutionId;
    private String outboundPayloadId;

    @BeforeEach
    void seedOperationalEvidence() {
        suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        workload = "operations-" + suffix;
        artifactId = "policy-operations-" + suffix;
        executionId = "exec_operations_" + suffix;
        recoveryId = "recovery_operations_" + suffix;
        connectorExecutionId = "connector_operations_" + suffix;
        outboundPayloadId = "outbound_operations_" + suffix;
        OffsetDateTime now = OffsetDateTime.now();
        jdbcClient.sql("""
                insert into policy.lifecycle_artifact (
                    artifact_id, artifact_version, artifact_digest, institution_id, policy_layer,
                    execution_pack, workload_id, purpose_code, lifecycle_stage, created_by,
                    revision, created_at, updated_at
                ) values (
                    :artifactId, '1.0.0', :digest, 'institution_local', 'WORKLOAD',
                    'AI', :workload, 'CUSTOMER_SUPPORT', 'ACTIVE', 'maker-operations',
                    3, :now, :now
                )
                """)
            .param("artifactId", artifactId)
            .param("digest", "a".repeat(64))
            .param("workload", workload)
            .param("now", now)
            .update();
        jdbcClient.sql("""
                insert into runtime.runtime_execution (
                    execution_id, request_id, trace_id, idempotency_key, workload_id,
                    idempotency_institution_id, request_hash, purpose_code, input_digest,
                    institution_id, status, created_at, updated_at
                ) values (
                    :executionId, :requestId, :traceId, :idempotencyKey, :workload,
                    'institution_local', :requestHash, 'CUSTOMER_SUPPORT', :inputDigest,
                    'institution_local', 'EGRESSING', :now, :now
                )
                """)
            .param("executionId", executionId)
            .param("requestId", "request_runtime_" + suffix)
            .param("traceId", "trace_runtime_" + suffix)
            .param("idempotencyKey", "idempotency_runtime_" + suffix)
            .param("workload", workload)
            .param("requestHash", "b".repeat(64))
            .param("inputDigest", "c".repeat(64))
            .param("now", now)
            .update();
        jdbcClient.sql("""
                insert into runtime.outbound_candidate (
                    outbound_payload_id, execution_id, destination_profile_id,
                    destination_profile_version, destination_profile_digest, pack_type,
                    schema_version, candidate_payload_digest, field_count, guard_status,
                    guard_reason_codes, created_at
                ) values (
                    :outboundPayloadId, :executionId, 'destination-operations', '1.0.0',
                    :destinationDigest, 'AI', 'operations/v1', :candidateDigest, 1,
                    'PASSED', '', :now
                )
                """)
            .param("outboundPayloadId", outboundPayloadId)
            .param("executionId", executionId)
            .param("destinationDigest", "d".repeat(64))
            .param("candidateDigest", "e".repeat(64))
            .param("now", now)
            .update();
        jdbcClient.sql("""
                insert into runtime.connector_execution (
                    connector_execution_id, execution_id, outbound_payload_id,
                    outbound_candidate_digest, connector_id, status, created_at
                ) values (
                    :connectorExecutionId, :executionId, :outboundPayloadId,
                    :candidateDigest, 'connector-operations', 'SENT_UNKNOWN', :now
                )
                """)
            .param("connectorExecutionId", connectorExecutionId)
            .param("executionId", executionId)
            .param("outboundPayloadId", outboundPayloadId)
            .param("candidateDigest", "e".repeat(64))
            .param("now", now)
            .update();
        jdbcClient.sql("""
                insert into runtime.external_interaction_recovery (
                    recovery_id, execution_id, connector_execution_id, connector_id,
                    provider_correlation_key, observed_status, recovery_status, retry_disposition,
                    attempt_count, max_attempts, next_attempt_at, created_at, updated_at
                ) values (
                    :recoveryId, :executionId, :connectorExecutionId, 'connector-operations',
                    'provider-operations', 'SENT_UNKNOWN', 'PENDING', 'RECONCILE_FIRST',
                    0, 5, :now, :now, :now
                )
                """)
            .param("recoveryId", recoveryId)
            .param("executionId", executionId)
            .param("connectorExecutionId", connectorExecutionId)
            .param("now", now)
            .update();
        jdbcClient.sql("""
                insert into runtime.recovery_operation_event (
                    event_id, operation_id, recovery_id, execution_id, institution_id,
                    workload_id, actor_principal_id, operation_type, outcome, created_at
                ) values (
                    :eventId, :operationId, :recoveryId, :executionId, 'institution_local',
                    :workload, 'operator-operations', 'RECONCILE', 'IN_PROGRESS', :createdAt
                )
                """)
            .param("eventId", "event_operations_" + suffix)
            .param("operationId", "operation_operations_" + suffix)
            .param("recoveryId", recoveryId)
            .param("executionId", executionId)
            .param("workload", workload)
            .param("createdAt", now.minusMinutes(10))
            .update();
        jdbcClient.sql("""
                insert into policy.lifecycle_transition_event (
                    institution_id, artifact_id, artifact_version, from_stage, to_stage, actor_id,
                    reason_code, artifact_digest, occurred_at, approval_gate_version
                ) values (
                    'institution_local', :artifactId, '1.0.0', 'APPROVED', 'ACTIVE',
                    'checker-operations', 'ACTIVATION_APPROVED', :digest, :now, 'NOT_APPLICABLE'
                )
                """)
            .param("artifactId", artifactId)
            .param("digest", "a".repeat(64))
            .param("now", now)
            .update();
        jdbcClient.sql("""
                insert into policy.current_selection (
                    institution_id, policy_layer, execution_pack, workload_id, purpose_code,
                    artifact_id, artifact_version, artifact_digest, artifact_revision,
                    selection_revision, selected_by, selected_at
                ) values (
                    'institution_local', 'WORKLOAD', 'AI', :workload, 'CUSTOMER_SUPPORT',
                    :artifactId, '1.0.0', :digest, 3, 1, 'checker-operations', :now
                )
                """)
            .param("workload", workload)
            .param("artifactId", artifactId)
            .param("digest", "a".repeat(64))
            .param("now", now)
            .update();
        jdbcClient.sql("""
                insert into policy.current_selection_event (
                    institution_id, policy_layer, execution_pack, workload_id, purpose_code,
                    event_type, selected_artifact_id, selected_artifact_version,
                    selected_artifact_digest, selected_artifact_revision, selection_revision,
                    actor_id, reason_code, occurred_at
                ) values (
                    'institution_local', 'WORKLOAD', 'AI', :workload, 'CUSTOMER_SUPPORT',
                    'ACTIVATED', :artifactId, '1.0.0', :digest, 3, 1,
                    'checker-operations', 'ACTIVATION_APPROVED', :now
                )
                """)
            .param("workload", workload)
            .param("artifactId", artifactId)
            .param("digest", "a".repeat(64))
            .param("now", now)
            .update();
        jdbcClient.sql("""
                insert into runtime.request_attempt (
                    attempt_id, request_id, trace_id, principal_id, institution_id,
                    workload_id, purpose_code, authorization_result, reason_code, created_at
                ) values (
                    :attemptId, :requestId, :traceId, 'principal-operations', 'institution_local',
                    :workload, 'CUSTOMER_SUPPORT', 'DENIED', 'AUTHORIZATION_POLICY_DENIED', :now
                )
                """)
            .param("attemptId", "attempt_" + suffix)
            .param("requestId", "request_" + suffix)
            .param("traceId", "trace_" + suffix)
            .param("workload", workload)
            .param("now", now)
            .update();
    }

    @AfterEach
    void cleanUpOperationalEvidence() {
        jdbcClient.sql("delete from runtime.recovery_operation_event where recovery_id = :recoveryId")
            .param("recoveryId", recoveryId).update();
        jdbcClient.sql("delete from runtime.external_interaction_recovery where recovery_id = :recoveryId")
            .param("recoveryId", recoveryId).update();
        jdbcClient.sql("delete from runtime.connector_execution where connector_execution_id = :id")
            .param("id", connectorExecutionId).update();
        jdbcClient.sql("delete from runtime.outbound_candidate where outbound_payload_id = :id")
            .param("id", outboundPayloadId).update();
        jdbcClient.sql("delete from runtime.runtime_execution where execution_id = :id")
            .param("id", executionId).update();
        jdbcClient.sql("delete from runtime.request_attempt where attempt_id = :id")
            .param("id", "attempt_" + suffix).update();
        jdbcClient.sql("delete from policy.current_selection_event where workload_id = :workload")
            .param("workload", workload).update();
        jdbcClient.sql("delete from policy.current_selection where workload_id = :workload")
            .param("workload", workload).update();
        jdbcClient.sql("delete from policy.lifecycle_transition_event where artifact_id = :artifactId")
            .param("artifactId", artifactId).update();
        jdbcClient.sql("delete from policy.lifecycle_artifact where artifact_id = :artifactId")
            .param("artifactId", artifactId).update();
    }

    @Test
    void auditorReadsSummaryAndCombinedPolicyEvidence() throws Exception {
        mockMvc.perform(get("/api/admin/operations/summary")
                .header("X-ADP-User-Id", "auditor-operations")
                .header("X-ADP-User-Roles", "AUDITOR")
                .param("windowMinutes", "60"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.schemaVersion").value("adp-operations-summary/v1"))
            .andExpect(jsonPath("$.policy.currentSelections").isNumber())
            .andExpect(jsonPath("$.policy.activations").isNumber())
            .andExpect(jsonPath("$.security.deniedAttempts").isNumber())
            .andExpect(jsonPath("$.recovery.staleOperations").value(1))
            .andExpect(jsonPath("$.recovery.oldestStaleOperationAgeSeconds").isNumber());

        mockMvc.perform(get("/api/admin/operations/policy-events")
                .header("X-ADP-User-Id", "auditor-operations")
                .header("X-ADP-User-Roles", "AUDITOR")
                .param("workloadId", workload))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(2))
            .andExpect(jsonPath("$.items[0].workloadId").value(workload));
    }

    @Test
    void scopedSummaryDetectsDriftAndRejectsOtherWorkloads() {
        OffsetDateTime now = OffsetDateTime.now();
        var healthy = port.loadSummary(
            "institution_local", Set.of(workload), now.minusHours(1), now, 60
        );
        assertThat(healthy.policy().currentSelections()).isEqualTo(1);
        assertThat(healthy.policy().driftedSelections()).isZero();
        assertThat(healthy.security().authorizationPolicyDenied()).isEqualTo(1);
        assertThat(healthy.recovery().staleOperations()).isEqualTo(1);
        assertThat(healthy.recovery().oldestStaleOperationAgeSeconds()).isGreaterThanOrEqualTo(600);

        jdbcClient.sql("""
                update policy.lifecycle_artifact set lifecycle_stage = 'REVIEW'
                where institution_id = 'institution_local' and artifact_id = :artifactId
                """)
            .param("artifactId", artifactId)
            .update();
        var drifted = port.loadSummary(
            "institution_local", Set.of(workload), now.minusHours(1), now, 60
        );
        var forbiddenScope = port.loadPolicyEvents(
            "institution_local", Set.of("another-workload"), null, null, null, null, 0, 10
        );

        assertThat(drifted.policy().driftedSelections()).isEqualTo(1);
        assertThat(forbiddenScope.items()).isEmpty();
    }

    @Test
    void operationsEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/admin/operations/summary"))
            .andExpect(status().isUnauthorized());
    }
}
