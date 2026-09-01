package com.adp.gateway.runtime.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.adp.gateway.auth.application.ApiKeyHasher;
import org.junit.jupiter.api.Test;
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
class RuntimeExecutionControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private ApiKeyHasher apiKeyHasher;

    @Test
    void createsRuntimeExecutionThroughCanonicalContextAndDecision() throws Exception {
        String suffix = token();
        String requestId = "req_v1_" + suffix;
        String traceId = "trace_v1_" + suffix;
        String idempotencyKey = "idem_v1_" + suffix;
        String response = mockMvc.perform(post("/v1/runtime/executions")
                .header("X-Request-Id", requestId)
                .header("X-Trace-Id", traceId)
                .header("X-ADP-API-Key", "local-dev-api-key")
                .contentType("application/json")
                .content("""
                    {
                      "workloadId": "customer_summary",
                      "purposeCode": "CUSTOMER_SUPPORT",
                      "subjectScope": "customer:customer-100",
                      "destinationProfileId": "dest_internal_provider_project_provisional",
                      "idempotencyKey": "%s",
                      "processingContexts": ["AI_USE"],
                      "input": {
                        "ticketId": "ticket-100"
                      }
                    }
                    """.formatted(idempotencyKey)))
            .andExpect(status().isOk())
            .andExpect(header().string("X-Request-Id", requestId))
            .andExpect(header().string("X-Trace-Id", traceId))
            .andExpect(jsonPath("$.executionId").exists())
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.policyAction").value("TRANSFORM"))
            .andExpect(jsonPath("$.finalAction").value("TRANSFORM"))
            .andExpect(jsonPath("$.authorizationResult").value("ALLOWED"))
            .andExpect(jsonPath("$.applicabilityResult").value("APPLICABLE"))
            .andExpect(jsonPath("$.runtimeContextDigest").exists())
            .andExpect(jsonPath("$.policyVersion").value("be-runtime-policy/0.0.0"))
            .andExpect(jsonPath("$.snapshotDigest").value("be-snapshot-local-fixture:customer-summary:customer-support:internal-provider"))
            .andExpect(jsonPath("$.sourceArtifactId").value("PROJECT_PROVISIONAL_POLICY_EVALUATION"))
            .andExpect(jsonPath("$.sourceArtifactVersion").value("0.0.0"))
            .andExpect(jsonPath("$.sourceArtifactDigestAlgorithm").value("sha256"))
            .andExpect(jsonPath("$.sourceArtifactDigestValue").value("local-fixture-policy-evaluation"))
            .andExpect(jsonPath("$.privacySafeOutput.status").value("APPLIED"))
            .andExpect(jsonPath("$.privacySafeOutput.outputDigest").exists())
            .andExpect(jsonPath("$.privacySafeOutput.fieldCount").value(13))
            .andExpect(jsonPath("$.privacySafeOutput.fields[*].strategy").isArray())
            .andExpect(jsonPath("$.privacySafeOutput.fields[*].sourceValueDigest").doesNotExist())
            .andExpect(jsonPath("$.privacySafeOutput.fields[*].transformedValueDigest").doesNotExist())
            .andExpect(jsonPath("$.privacySafeOutput.fields[*].transformedValue").doesNotExist())
            .andExpect(jsonPath("$.outboundCandidateDigest").exists())
            .andExpect(jsonPath("$.outboundGuardStatus").value("PASSED"))
            .andExpect(jsonPath("$.connectorStatus").value("ACKNOWLEDGED"))
            .andExpect(jsonPath("$.responseGuardStatus").value("PASSED"))
            .andReturn()
            .getResponse()
            .getContentAsString();

        String executionId = response.replaceAll(".*\\\"executionId\\\":\\\"([^\\\"]+)\\\".*", "$1");

        Integer executionCount = jdbcClient.sql("""
                select count(*) from runtime.runtime_execution
                where execution_id = :executionId
                  and status = 'COMPLETED'
                  and provider_profile_id = 'internal-provider'
                  and input_digest is not null
                  and canonical_context_digest is not null
                  and runtime_context_digest is not null
                  and decision_id is not null
                  and outbound_candidate_digest is not null
                  and destination_profile_id = 'dest_internal_provider_project_provisional'
                  and destination_profile_version = '0.0.0'
                  and destination_profile_digest = 'local-fixture-destination-profile'
                  and outbound_guard_status = 'PASSED'
                  and connector_status = 'ACKNOWLEDGED'
                  and response_guard_status = 'PASSED'
                """)
            .param("executionId", executionId)
            .query(Integer.class)
            .single();
        assertThat(executionCount).isGreaterThanOrEqualTo(1);

        Integer decisionCount = jdbcClient.sql("""
                select count(*) from runtime.runtime_decision
                where execution_id = :executionId
                  and final_action = 'TRANSFORM'
                  and applicability_result = 'APPLICABLE'
                """)
            .param("executionId", executionId)
            .query(Integer.class)
            .single();
        assertThat(decisionCount).isGreaterThanOrEqualTo(1);

        mockMvc.perform(get("/v1/runtime/executions/{executionId}/trace", executionId)
                .header("X-ADP-API-Key", "local-dev-api-key"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.executionId").value(executionId))
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.stages[0].stage").value("RECEIVED"))
            .andExpect(jsonPath("$.stages[1].stage").value("AUTHORIZATION"))
            .andExpect(jsonPath("$.stages[2].stage").value("RETRIEVAL"))
            .andExpect(jsonPath("$.stages[3].stage").value("CANONICAL_CONTEXT"))
            .andExpect(jsonPath("$.stages[4].stage").value("DECISION"))
            .andExpect(jsonPath("$.stages[5].stage").value("TRANSFORM"))
            .andExpect(jsonPath("$.stages[6].stage").value("OUTBOUND_GUARD"))
            .andExpect(jsonPath("$.stages[7].stage").value("CONNECTOR"))
            .andExpect(jsonPath("$.stages[8].stage").value("RESPONSE_GUARD"));

        Integer transformCount = jdbcClient.sql("""
                select count(*)
                from runtime.transform_execution te
                join runtime.transform_field tf on tf.transform_execution_id = te.transform_execution_id
                where te.execution_id = :executionId
                  and te.status = 'APPLIED'
                  and te.output_digest is not null
                  and tf.source_value_digest is not null
                  and tf.transformed_value_digest is not null
                  and tf.strategy_version is not null
                  and tf.key_version is not null
                  and tf.mapping_version is not null
                  and tf.instruction_digest is not null
                  and tf.strategy in ('VAULT_TOKEN', 'GENERALIZE', 'KEEP')
                """)
            .param("executionId", executionId)
            .query(Integer.class)
            .single();
        assertThat(transformCount).isGreaterThanOrEqualTo(1);

        Integer egressCount = jdbcClient.sql("""
                select count(*)
                from runtime.outbound_candidate oc
                join runtime.connector_execution ce on ce.outbound_payload_id = oc.outbound_payload_id
                join runtime.response_guard_result rg on rg.execution_id = oc.execution_id
                where oc.execution_id = :executionId
                  and oc.guard_status = 'PASSED'
                  and oc.candidate_payload_digest is not null
                  and oc.field_count > 0
                  and ce.status = 'ACKNOWLEDGED'
                  and ce.outbound_candidate_digest = oc.candidate_payload_digest
                  and rg.status = 'PASSED'
                  and rg.leakage_detected = false
                """)
            .param("executionId", executionId)
            .query(Integer.class)
            .single();
        assertThat(egressCount).isEqualTo(1);

        Integer vaultCount = jdbcClient.sql("""
                select count(*)
                from vault.token_mapping
                where data_class = 'CUSTOMER_IDENTIFIER'
                  and status = 'ACTIVE'
                  and length(mapping_scope) = 64
                  and source_value_digest is not null
                  and token_ref is not null
                """)
            .query(Integer.class)
            .single();
        assertThat(vaultCount).isGreaterThanOrEqualTo(1);
    }

    @Test
    void differentInputProducesDifferentInputAndRuntimeContextDigest() throws Exception {
        String suffix = token();
        String requestA = "req_in_a_" + suffix;
        String requestB = "req_in_b_" + suffix;
        postRuntimeExecution(requestA, "trace_in_a_" + suffix, "idem_in_a_" + suffix, "ticket-100");
        postRuntimeExecution(requestB, "trace_in_b_" + suffix, "idem_in_b_" + suffix, "ticket-999");

        String inputDigestA = digest("input_digest", requestA);
        String inputDigestB = digest("input_digest", requestB);
        String runtimeDigestA = digest("runtime_context_digest", requestA);
        String runtimeDigestB = digest("runtime_context_digest", requestB);

        assertThat(inputDigestA).isNotEqualTo(inputDigestB);
        assertThat(runtimeDigestA).isNotEqualTo(runtimeDigestB);
    }

    @Test
    void duplicateIdempotencyKeyForSameWorkloadIsRejected() throws Exception {
        String suffix = token();
        String idempotencyKey = "idem_dup_" + suffix;
        postRuntimeExecution("req_dup_a_" + suffix, "trace_dup_a_" + suffix, idempotencyKey, "ticket-100");

        mockMvc.perform(post("/v1/runtime/executions")
                .header("X-Request-Id", "req_dup_b_" + suffix)
                .header("X-Trace-Id", "trace_dup_b_" + suffix)
                .header("X-ADP-API-Key", "local-dev-api-key")
                .contentType("application/json")
                .content(runtimeRequest(idempotencyKey, "ticket-999")))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.errorCode").value("IDEMPOTENCY_KEY_REUSED"))
            .andExpect(jsonPath("$.reasonCode").value("IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    void rejectsExecutionReadWhenPrincipalCannotAccessWorkload() throws Exception {
        String suffix = token();
        String response = postRuntimeExecution(
            "req_bola_" + suffix,
            "trace_bola_" + suffix,
            "idem_bola_" + suffix,
            "ticket-100"
        );
        String executionId = response.replaceAll(".*\\\"executionId\\\":\\\"([^\\\"]+)\\\".*", "$1");
        insertFraudOnlyPrincipal("fraud-only-key-" + suffix);

        mockMvc.perform(get("/v1/runtime/executions/{executionId}", executionId)
                .header("X-ADP-API-Key", "fraud-only-key-" + suffix))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.errorCode").value("AUTHORIZATION_DENIED"));

        mockMvc.perform(get("/v1/runtime/executions/{executionId}/trace", executionId)
                .header("X-ADP-API-Key", "fraud-only-key-" + suffix))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.errorCode").value("AUTHORIZATION_DENIED"));
    }

    @Test
    void rejectsRequestFieldsLongerThanDatabaseContract() throws Exception {
        mockMvc.perform(post("/v1/runtime/executions")
                .header("X-Request-Id", "req_size_" + token())
                .header("X-Trace-Id", "trace_size_" + token())
                .header("X-ADP-API-Key", "local-dev-api-key")
                .contentType("application/json")
                .content("""
                    {
                      "workloadId": "%s",
                      "purposeCode": "CUSTOMER_SUPPORT",
                      "subjectScope": "customer:customer-100",
                      "destinationProfileId": "dest_internal_provider_project_provisional",
                      "idempotencyKey": "idem-size",
                      "processingContexts": ["AI_USE"],
                      "input": {}
                    }
                    """.formatted("x".repeat(121))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    void returnsNotFoundForUnknownExecutionId() throws Exception {
        mockMvc.perform(get("/v1/runtime/executions/exec_not_exists")
                .header("X-ADP-API-Key", "local-dev-api-key"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.errorCode").value("RUNTIME_EXECUTION_NOT_FOUND"))
            .andExpect(jsonPath("$.reasonCode").value("RUNTIME_EXECUTION_NOT_FOUND"));

        mockMvc.perform(get("/v1/runtime/executions/exec_not_exists/trace")
                .header("X-ADP-API-Key", "local-dev-api-key"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.errorCode").value("RUNTIME_EXECUTION_NOT_FOUND"))
            .andExpect(jsonPath("$.reasonCode").value("RUNTIME_EXECUTION_NOT_FOUND"));
    }

    @Test
    void rejectsInvalidProcessingContextElements() throws Exception {
        mockMvc.perform(post("/v1/runtime/executions")
                .header("X-Request-Id", "req_pc_" + token())
                .header("X-Trace-Id", "trace_pc_" + token())
                .header("X-ADP-API-Key", "local-dev-api-key")
                .contentType("application/json")
                .content("""
                    {
                      "workloadId": "customer_summary",
                      "purposeCode": "CUSTOMER_SUPPORT",
                      "subjectScope": "customer:customer-100",
                      "destinationProfileId": "dest_internal_provider_project_provisional",
                      "idempotencyKey": "idem-pc",
                      "processingContexts": ["AI_USE", null],
                      "input": {}
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    void unknownDestinationProfileIsBlockedAndRecorded() throws Exception {
        String suffix = token();
        String requestId = "req_dest_" + suffix;

        mockMvc.perform(post("/v1/runtime/executions")
                .header("X-Request-Id", requestId)
                .header("X-Trace-Id", "trace_dest_" + suffix)
                .header("X-ADP-API-Key", "local-dev-api-key")
                .contentType("application/json")
                .content("""
                    {
                      "workloadId": "customer_summary",
                      "purposeCode": "CUSTOMER_SUPPORT",
                      "subjectScope": "customer:customer-100",
                      "destinationProfileId": "dest_missing",
                      "idempotencyKey": "idem_dest_%s",
                      "processingContexts": ["AI_USE"],
                      "input": {}
                    }
                    """.formatted(suffix)))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.errorCode").value("DESTINATION_PROFILE_NOT_FOUND"));

        Integer blockedCount = jdbcClient.sql("""
                select count(*)
                from runtime.runtime_execution
                where request_id = :requestId
                  and destination_profile_id = 'dest_missing'
                  and provider_profile_id is null
                  and status = 'BLOCKED'
                """)
            .param("requestId", requestId)
            .query(Integer.class)
            .single();
        assertThat(blockedCount).isEqualTo(1);
    }

    private String postRuntimeExecution(
        String requestId,
        String traceId,
        String idempotencyKey,
        String ticketId
    ) throws Exception {
        return mockMvc.perform(post("/v1/runtime/executions")
                .header("X-Request-Id", requestId)
                .header("X-Trace-Id", traceId)
                .header("X-ADP-API-Key", "local-dev-api-key")
                .contentType("application/json")
                .content(runtimeRequest(idempotencyKey, ticketId)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    }

    private String runtimeRequest(String idempotencyKey, String ticketId) {
        return """
            {
              "workloadId": "customer_summary",
              "purposeCode": "CUSTOMER_SUPPORT",
              "subjectScope": "customer:customer-100",
              "destinationProfileId": "dest_internal_provider_project_provisional",
              "idempotencyKey": "%s",
              "processingContexts": ["AI_USE"],
              "input": {
                "ticketId": "%s"
              }
            }
            """.formatted(idempotencyKey, ticketId);
    }

    private void insertFraudOnlyPrincipal(String apiKey) {
        jdbcClient.sql("""
                insert into auth_principal (
                    principal_id, principal_type, display_name, subject_authorization_required, enabled
                )
                values ('svc_fraud_runtime', 'SERVICE', 'Fraud Runtime', false, true)
                on conflict (principal_id) do nothing
                """)
            .update();
        jdbcClient.sql("""
                insert into auth_principal_role (principal_id, role_name)
                values ('svc_fraud_runtime', 'RUNTIME_EXECUTOR')
                on conflict (principal_id, role_name) do nothing
                """)
            .update();
        jdbcClient.sql("""
                insert into auth_principal_workload (principal_id, workload_id)
                values ('svc_fraud_runtime', 'fraud_analysis')
                on conflict (principal_id, workload_id) do nothing
                """)
            .update();
        jdbcClient.sql("""
                insert into auth_api_key (key_id, principal_id, key_hash, enabled)
                values (:keyId, 'svc_fraud_runtime', :keyHash, true)
                on conflict (key_id) do nothing
                """)
            .param("keyId", "key_fraud_runtime_" + token())
            .param("keyHash", apiKeyHasher.hash(apiKey))
            .update();
    }

    private String token() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private String digest(String column, String requestId) {
        return jdbcClient.sql("""
                select %s from runtime.runtime_execution
                where request_id = :requestId
                order by id desc
                limit 1
                """.formatted(column))
            .param("requestId", requestId)
            .query(String.class)
            .single();
    }
}
