package com.adp.gateway.recovery.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.adp.gateway.connector.domain.ConnectorResult;
import com.adp.gateway.connector.domain.ConnectorStatus;
import com.adp.gateway.recovery.domain.ExternalStatusQueryResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
    "adp.local-fixtures.enabled=true",
    "adp.mock-runtime.enabled=true"
})
@AutoConfigureMockMvc
class JdbcExternalInteractionRecoveryPersistenceTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private JdbcExternalInteractionRecoveryPersistence persistence;

    @Test
    void reconciliationConvergesRecoveryConnectorAndRuntimeThenAllowsReplay() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String idempotencyKey = "idem_recovery_" + suffix;
        String request = request(idempotencyKey);
        String response = execute("req_recovery_" + suffix, "trace_recovery_" + suffix, request);
        String executionId = response.replaceAll(".*\\\"executionId\\\":\\\"([^\\\"]+)\\\".*", "$1");
        RecoverySeed seed = jdbcClient.sql("""
                select re.connector_execution_id, ce.connector_id, re.outbound_payload_id,
                       re.outbound_candidate_digest, re.provider_request_id
                from runtime.runtime_execution re
                join runtime.connector_execution ce on ce.connector_execution_id = re.connector_execution_id
                where re.execution_id = :executionId
                """)
            .param("executionId", executionId)
            .query(RecoverySeed.class)
            .single();
        jdbcClient.sql("""
                update runtime.runtime_execution
                set status = 'EGRESSING', connector_status = 'SENT_UNKNOWN'
                where execution_id = :executionId
                """)
            .param("executionId", executionId)
            .update();
        jdbcClient.sql("""
                update runtime.connector_execution set status = 'SENT_UNKNOWN'
                where connector_execution_id = :connectorExecutionId
                """)
            .param("connectorExecutionId", seed.connectorExecutionId())
            .update();
        OffsetDateTime now = OffsetDateTime.now();
        persistence.scheduleUnknown(executionId, new ConnectorResult(
            seed.connectorExecutionId(), seed.connectorId(), ConnectorStatus.SENT_UNKNOWN,
            seed.outboundPayloadId(), seed.outboundCandidateDigest(), null, null
        ), now);

        var claimed = persistence.claimNext("worker-test", now.plusSeconds(1), Duration.ofSeconds(30)).orElseThrow();
        assertThat(claimed.providerCorrelationKey()).isEqualTo(seed.providerRequestId());
        assertThat(persistence.reconcile(
            claimed.recoveryId(), "worker-test",
            new ExternalStatusQueryResult(ConnectorStatus.ACKNOWLEDGED, "a".repeat(64)),
            now.plusSeconds(2)
        )).isTrue();

        ConvergedState state = jdbcClient.sql("""
                select re.status as runtime_status, re.connector_status,
                       ce.status as connector_execution_status, rr.recovery_status,
                       rr.last_observed_external_status, rr.status_query_evidence_digest
                from runtime.runtime_execution re
                join runtime.connector_execution ce on ce.connector_execution_id = re.connector_execution_id
                join runtime.external_interaction_recovery rr on rr.execution_id = re.execution_id
                where re.execution_id = :executionId
                """)
            .param("executionId", executionId)
            .query(ConvergedState.class)
            .single();
        assertThat(state.runtimeStatus()).isEqualTo("EXTERNALLY_RECONCILED");
        assertThat(state.connectorStatus()).isEqualTo("ACKNOWLEDGED");
        assertThat(state.connectorExecutionStatus()).isEqualTo("ACKNOWLEDGED");
        assertThat(state.recoveryStatus()).isEqualTo("RECONCILED");
        assertThat(state.lastObservedExternalStatus()).isEqualTo("ACKNOWLEDGED");
        assertThat(state.statusQueryEvidenceDigest()).hasSize(64);

        mockMvc.perform(post("/v1/runtime/executions")
                .header("X-Request-Id", "req_replay_" + suffix)
                .header("X-Trace-Id", "trace_replay_" + suffix)
                .header("X-ADP-API-Key", "local-dev-api-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.executionId").value(executionId))
            .andExpect(jsonPath("$.status").value("EXTERNALLY_RECONCILED"))
            .andExpect(jsonPath("$.replayed").value(true));
    }

    private String execute(String requestId, String traceId, String request) throws Exception {
        return mockMvc.perform(post("/v1/runtime/executions")
                .header("X-Request-Id", requestId)
                .header("X-Trace-Id", traceId)
                .header("X-ADP-API-Key", "local-dev-api-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
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
              "input":{"prompt":"safe recovery question"}
            }
            """.formatted(idempotencyKey);
    }

    private record RecoverySeed(
        String connectorExecutionId,
        String connectorId,
        String outboundPayloadId,
        String outboundCandidateDigest,
        String providerRequestId
    ) {
    }

    private record ConvergedState(
        String runtimeStatus,
        String connectorStatus,
        String connectorExecutionStatus,
        String recoveryStatus,
        String lastObservedExternalStatus,
        String statusQueryEvidenceDigest
    ) {
    }
}
