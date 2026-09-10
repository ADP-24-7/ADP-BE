package com.adp.gateway.digitalasset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

import com.adp.gateway.recovery.application.ExternalInteractionRecoveryService;
import com.adp.gateway.recovery.application.ExternalInteractionRecoveryPersistence;
import com.adp.gateway.recovery.application.ExternalStatusQueryPermanentException;
import com.adp.gateway.recovery.application.RecoveryReconciliationCoordinator;
import com.adp.gateway.recovery.application.StaleRecoveryLeaseException;
import com.adp.gateway.recovery.domain.ExternalStatusQueryResult;
import com.adp.gateway.connector.domain.ConnectorStatus;
import com.adp.gateway.audit.application.AuditReadPort;
import com.adp.gateway.digitalasset.infrastructure.FakeDigitalAssetPlatformStateStore;

@SpringBootTest(properties = {
    "adp.local-fixtures.enabled=true",
    "adp.mock-runtime.enabled=true"
})
@AutoConfigureMockMvc
class DigitalAssetThinE2ETests {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private ExternalInteractionRecoveryService recoveryService;

    @Autowired
    private ExternalInteractionRecoveryPersistence recoveryPersistence;

    @Autowired
    private RecoveryReconciliationCoordinator reconciliationCoordinator;

    @Autowired
    private AuditReadPort auditReadPort;

    @Autowired
    private FakeDigitalAssetPlatformStateStore platformStateStore;

    @Test
    void executesTokenizedAssetPurchaseWithSettlementAndReconciliationEvidence() throws Exception {
        double completedBefore = terminalTransitions("COMPLETED");
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String response = mockMvc.perform(post("/v1/runtime/executions")
                .header("X-Request-Id", "req_asset_" + suffix)
                .header("X-Trace-Id", "trace_asset_" + suffix)
                .header("X-ADP-API-Key", "local-dev-api-key")
                .contentType("application/json")
                .content("""
                    {
                      "institutionId":"institution_local",
                      "approvalReference":"approval_digital_asset_purchase_v1",
                      "workloadId":"tokenized_asset_purchase",
                      "purposeCode":"DIGITAL_ASSET_PURCHASE",
                      "subjectScope":"customer:customer-100",
                      "destinationProfileId":"dest_mock_asset_platform_v1",
                      "idempotencyKey":"idem_asset_%s",
                      "processingContexts":["DIGITAL_ASSET"],
                      "input":{
                        "approvedTransactionReference":"approved-tx-local-001",
                        "customerId":"customer-100",
                        "accountId":"acct-100-1",
                        "outboundRequest":{
                          "requestedAsset":{
                            "chainId":"eip155:1",
                            "assetKind":"FUNGIBLE_TOKEN",
                            "assetSymbol":"asset-krw-token-001",
                            "assetContractAddress":"0x0000000000000000000000000000000000000001",
                            "operation":"TRANSFER",
                            "tokenId":null
                          },
                          "requestedAmount":"10000",
                          "requestedDestination":"wallet-test-001",
                          "requestedBeneficiaryReference":"beneficiary-local-001",
                          "regulatoryOutboundData":{}
                        }
                      }
                    }
                    """.formatted(suffix)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.policyAction").value("TRANSFORM"))
            .andExpect(jsonPath("$.applicabilityResult").value("APPLICABLE"))
            .andExpect(jsonPath("$.connectorStatus").value("ACKNOWLEDGED"))
            .andExpect(jsonPath("$.responseGuardStatus").value("PASSED"))
            .andExpect(jsonPath("$.output.content").value("SETTLED"))
            .andReturn().getResponse().getContentAsString();
        String executionId = response.replaceAll(".*\\\"executionId\\\":\\\"([^\\\"]+)\\\".*", "$1");

        Integer tokenizedIdentifiers = jdbcClient.sql("""
                select count(*)
                from runtime.transform_execution te
                join runtime.transform_field tf on tf.transform_execution_id = te.transform_execution_id
                where te.execution_id = :executionId
                  and tf.field_path in ('$.input.customerId', '$.input.accountId')
                  and tf.strategy = 'VAULT_TOKEN'
                """)
            .param("executionId", executionId).query(Integer.class).single();
        assertThat(tokenizedIdentifiers).isEqualTo(2);

        Integer exactFields = jdbcClient.sql("""
                select count(*)
                from runtime.transform_execution te
                join runtime.transform_field tf on tf.transform_execution_id = te.transform_execution_id
                where te.execution_id = :executionId
                  and tf.field_path in (
                    '$.input.outboundRequest.requestedAsset.chainId',
                    '$.input.outboundRequest.requestedAsset.assetKind',
                    '$.input.outboundRequest.requestedAsset.assetSymbol',
                    '$.input.outboundRequest.requestedAsset.assetContractAddress',
                    '$.input.outboundRequest.requestedAsset.operation',
                    '$.input.outboundRequest.requestedAmount',
                    '$.input.outboundRequest.requestedDestination'
                  )
                  and tf.strategy = 'KEEP'
                """)
            .param("executionId", executionId).query(Integer.class).single();
        assertThat(exactFields).isEqualTo(7);

        Integer removedBindingFields = jdbcClient.sql("""
                select count(*)
                from runtime.transform_execution te
                join runtime.transform_field tf on tf.transform_execution_id = te.transform_execution_id
                where te.execution_id = :executionId
                  and tf.field_path = '$.input.outboundRequest.requestedBeneficiaryReference'
                  and tf.strategy = 'REMOVE'
                  and tf.transformed_value_digest is null
                """)
            .param("executionId", executionId).query(Integer.class).single();
        assertThat(removedBindingFields).isEqualTo(1);

        Integer settlementEvidence = jdbcClient.sql("""
                select count(*) from runtime.digital_asset_transaction
                where execution_id = :executionId
                  and settlement_status = 'SETTLED'
                  and reconciliation_result = 'MATCH'
                  and external_transaction_id is not null
                  and settlement_id is not null
                  and provider_response_digest is not null
                """)
            .param("executionId", executionId).query(Integer.class).single();
        assertThat(settlementEvidence).isEqualTo(1);
        Integer snapshotCount = jdbcClient.sql("""
                select count(*) from runtime.digital_asset_runtime_snapshot
                where execution_id = :executionId
                  and artifact_id = 'DA-DIGITAL-ASSET-RUNTIME-LOCAL-ACTIVE-001'
                  and artifact_version = '1.0.0'
                  and snapshot_digest ~ '^sha256:[0-9a-f]{64}$'
                  and runtime_control_digest = 'sha256:94447f7910fa799caa4613dc80bc588104d5829e2042963affd98825bdd2c43c'
                  and crosswalk_digest = 'sha256:eb40822cdd5c192e68eb0fe3a961428dbabbdabda00204c760d32cac6b669cf5'
                """)
            .param("executionId", executionId).query(Integer.class).single();
        assertThat(snapshotCount).isEqualTo(1);
        Integer preExecutionGuardCount = jdbcClient.sql("""
                select count(*) from runtime.digital_asset_pre_execution_guard
                where execution_id = :executionId
                  and status = 'PASSED'
                  and (select count(*) from jsonb_each_text(control_results)) = 6
                  and not exists (
                    select 1 from jsonb_each_text(control_results) control where control.value <> 'PASSED'
                  )
                  and reason_codes = '[]'::jsonb
                  and outbound_payload_digest is not null
                  and provider_payload_digest is not null
                """)
            .param("executionId", executionId).query(Integer.class).single();
        assertThat(preExecutionGuardCount).isEqualTo(1);
        Integer postExecutionEvidenceCount = jdbcClient.sql("""
                select count(*) from runtime.digital_asset_post_execution_evidence
                where execution_id = :executionId
                  and status = 'VERIFIED'
                  and external_status = 'SETTLED'
                  and receipt_status = 'SUCCESS'
                  and finality_status = 'FINALIZED'
                  and amount_source = 'TOKEN_TRANSFER'
                  and mismatch_fields = '[]'::jsonb
                  and transaction_detail_digest ~ '^[0-9a-f]{64}$'
                  and receipt_finality_digest ~ '^[0-9a-f]{64}$'
                  and transfer_evidence_digest ~ '^[0-9a-f]{64}$'
                  and exact_amount_digest ~ '^[0-9a-f]{64}$'
                """)
            .param("executionId", executionId).query(Integer.class).single();
        assertThat(postExecutionEvidenceCount).isEqualTo(1);
        assertThat(terminalTransitions("COMPLETED")).isEqualTo(completedBefore + 1);

        mockMvc.perform(get("/v1/runtime/executions/{executionId}/trace", executionId)
                .header("X-ADP-API-Key", "local-dev-api-key"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.digitalAssetRuntimeSnapshot.snapshotId").exists())
            .andExpect(jsonPath("$.digitalAssetRuntimeSnapshot.artifactId")
                .value("DA-DIGITAL-ASSET-RUNTIME-LOCAL-ACTIVE-001"))
            .andExpect(jsonPath("$.digitalAssetRuntimeSnapshot.destinationProfileId")
                .value("dest_mock_asset_platform_v1"))
            .andExpect(jsonPath("$.digitalAssetPreExecutionGuard.status").value("PASSED"))
            .andExpect(jsonPath("$.digitalAssetPreExecutionGuard.controlResults.length()").value(6))
            .andExpect(jsonPath("$.stages[?(@.stage == 'PRE_EXECUTION_GUARD')].status").value("COMPLETED"))
            .andExpect(jsonPath("$.digitalAssetPostExecutionEvidence.status").value("VERIFIED"))
            .andExpect(jsonPath("$.digitalAssetPostExecutionEvidence.evidenceSourceType")
                .value("INDEPENDENT_EXTERNAL"))
            .andExpect(jsonPath("$.digitalAssetPostExecutionEvidence.amountSource").value("TOKEN_TRANSFER"))
            .andExpect(jsonPath("$.stages[?(@.stage == 'POST_EXECUTION_REBINDING')].status")
                .value("COMPLETED"))
            .andExpect(jsonPath("$.evidence.destinationProfileId").value("dest_mock_asset_platform_v1"))
            .andExpect(jsonPath("$.stages[?(@.stage == 'CONNECTOR')].status").value("COMPLETED"));

        var auditEvidence = auditReadPort.loadEvidence(
            executionId, "institution_local", java.util.Set.of("tokenized_asset_purchase")
        );
        assertThat(auditEvidence.digitalAssetRuntimeSnapshot()).isNotNull();
        assertThat(auditEvidence.digitalAssetRuntimeSnapshot().snapshotDigest())
            .matches("sha256:[0-9a-f]{64}");
        assertThat(auditEvidence.digitalAssetRuntimeSnapshot().destinationProfileDigest())
            .isEqualTo("local-digital-asset-destination-v1");

        assertThat(response)
            .doesNotContain("customer-100")
            .doesNotContain("acct-100-1")
            .doesNotContain("wallet-test-001")
            .doesNotContain("beneficiary-local-001");
    }

    @Test
    void rejectsCustomerIdThatDoesNotMatchAuthorizedSubjectBeforeConnector() throws Exception {
        String suffix = token();
        assetRequest(suffix, "customer-999", "asset-krw-token-001")
            .andExpect(status().isUnprocessableEntity());

        Integer connectorCount = jdbcClient.sql("""
                select count(*) from runtime.connector_execution ce
                join runtime.runtime_execution re on re.execution_id = ce.execution_id
                where re.request_id = :requestId
                """)
            .param("requestId", "req_asset_case_" + suffix).query(Integer.class).single();
        assertThat(connectorCount).isZero();
    }

    @Test
    void blocksApprovedTransactionTermMismatchesBeforeConnector() throws Exception {
        assertPolicyBlock(
            "asset", "approved-tx-local-001", "wallet-test-001", "asset-settling", "10000",
            "beneficiary-local-001", "DIGITAL_ASSET_APPROVED_ASSET_MISMATCH"
        );
        assertPolicyBlock(
            "amount", "approved-tx-local-001", "wallet-test-001", "asset-krw-token-001", "10000001",
            "beneficiary-local-001", "DIGITAL_ASSET_APPROVED_AMOUNT_EXCEEDED"
        );
        assertPolicyBlock(
            "destination", "approved-tx-local-001", "wallet-not-approved", "asset-krw-token-001", "10000",
            "beneficiary-local-001", "DIGITAL_ASSET_APPROVED_DESTINATION_MISMATCH"
        );
        assertPolicyBlock(
            "beneficiary", "approved-tx-local-001", "wallet-test-001", "asset-krw-token-001", "10000",
            "beneficiary-not-approved", "DIGITAL_ASSET_APPROVED_BENEFICIARY_MISMATCH"
        );
        assertPolicyBlock(
            "expired", "approved-tx-expired", "wallet-test-001", "asset-krw-token-001", "10000",
            "beneficiary-local-001", "DIGITAL_ASSET_APPROVED_PERIOD_VIOLATION"
        );
    }

    @Test
    void rejectsUnknownApprovedTransactionBeforeConnector() throws Exception {
        String suffix = "unknown_" + token();
        assetRequest(
            suffix, "customer-100", "approved-tx-unknown", "wallet-test-001",
            "asset-krw-token-001", "10000", "beneficiary-local-001"
        )
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.reasonCode").value("DIGITAL_ASSET_APPROVED_TRANSACTION_NOT_FOUND"));

        assertThat(connectorCount("req_asset_case_" + suffix)).isZero();
    }

    @Test
    void rejectsInvalidCallerContractWith422BeforeConnector() throws Exception {
        assertInvalidInput("zero_" + token(), "\"0\"", "{}");
        assertInvalidInput("numeric_" + token(), "10000", "{}");
        assertInvalidInput("fractional_" + token(), "1.5", "{}");
        assertInvalidInput("regulatory_" + token(), "\"10000\"", "{\"travelRule\":\"value\"}");
    }

    @Test
    void keepsRuntimeEgressingWhileSettlementIsNotFinal() throws Exception {
        assetRequest(token(), "customer-100", "asset-settling")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("EGRESSING"))
            .andExpect(jsonPath("$.output.deliveryStatus").value("WITHHELD"));
    }

    @Test
    void routesCriticalSettlementMismatchToReview() throws Exception {
        double reviewRequiredBefore = terminalTransitions("REVIEW_REQUIRED");
        String response = assetRequest(token(), "customer-100", "asset-critical-mismatch")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("REVIEW_REQUIRED"))
            .andExpect(jsonPath("$.output.deliveryStatus").value("WITHHELD"))
            .andReturn().getResponse().getContentAsString();
        String executionId = response.replaceAll(".*\\\"executionId\\\":\\\"([^\\\"]+)\\\".*", "$1");
        String reconciliation = jdbcClient.sql("""
                select reconciliation_result from runtime.digital_asset_transaction
                where execution_id = :executionId
                """)
            .param("executionId", executionId).query(String.class).single();
        assertThat(reconciliation).isEqualTo("CRITICAL_MISMATCH");
        assertMismatchCase(executionId, "CRITICAL_MISMATCH", "AMOUNT");
        assertThat(terminalTransitions("REVIEW_REQUIRED")).isEqualTo(reviewRequiredBefore + 1);
    }

    @Test
    void quarantinesNonCriticalMismatchWithoutAutomaticRetry() throws Exception {
        String response = assetRequest(token(), "customer-100", "asset-mismatch")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("REVIEW_REQUIRED"))
            .andExpect(jsonPath("$.output.deliveryStatus").value("WITHHELD"))
            .andReturn().getResponse().getContentAsString();
        String executionId = response.replaceAll(".*\\\"executionId\\\":\\\"([^\\\"]+)\\\".*", "$1");

        assertMismatchCase(executionId, "CRITICAL_MISMATCH", "RECIPIENT_ADDRESS");
    }

    @Test
    void rejectsProviderResponseFromAnotherExternalRequest() throws Exception {
        String response = assetRequest(token(), "customer-100", "asset-correlation-mismatch")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("REVIEW_REQUIRED"))
            .andExpect(jsonPath("$.output.deliveryStatus").value("WITHHELD"))
            .andReturn().getResponse().getContentAsString();
        String executionId = response.replaceAll(".*\\\"executionId\\\":\\\"([^\\\"]+)\\\".*", "$1");
        Integer transactionEvidenceCount = jdbcClient.sql("""
                select count(*) from runtime.digital_asset_transaction where execution_id = :executionId
                """)
            .param("executionId", executionId).query(Integer.class).single();
        assertThat(transactionEvidenceCount).isZero();
        assertMismatchCase(executionId, "CRITICAL_MISMATCH", "EXTERNAL_REQUEST_ID");
    }

    @Test
    void rejectsUntrustedProviderResultFieldBeforeOutcomeReconciliation() throws Exception {
        String response = assetRequest(token(), "customer-100", "asset-unexpected-field")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("BLOCKED"))
            .andExpect(jsonPath("$.responseGuardStatus").value("REJECTED"))
            .andExpect(jsonPath("$.output.deliveryStatus").value("WITHHELD"))
            .andReturn().getResponse().getContentAsString();
        String executionId = response.replaceAll(".*\\\"executionId\\\":\\\"([^\\\"]+)\\\".*", "$1");

        Integer mismatchCount = jdbcClient.sql("""
                select count(*) from runtime.digital_asset_mismatch_case where execution_id = :executionId
                """)
            .param("executionId", executionId)
            .query(Integer.class)
            .single();
        assertThat(mismatchCount).isZero();
    }

    @Test
    void reconcilesSentUnknownThroughDigitalAssetStatusQueryAdapter() throws Exception {
        String response = assetRequest(token(), "customer-100", "asset-sent-unknown")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("EGRESSING"))
            .andExpect(jsonPath("$.connectorStatus").value("SENT_UNKNOWN"))
            .andReturn().getResponse().getContentAsString();
        String executionId = response.replaceAll(".*\\\"executionId\\\":\\\"([^\\\"]+)\\\".*", "$1");
        Integer evidenceCount = jdbcClient.sql("""
                select count(*) from runtime.digital_asset_transaction
                where execution_id = :executionId and settlement_status = 'SENT_UNKNOWN'
                  and external_transaction_id is null and settlement_id is null
                  and provider_response_digest is null and reconciliation_result = 'WAIT'
                """)
            .param("executionId", executionId).query(Integer.class).single();
        Integer recoveryCount = jdbcClient.sql("""
                select count(*) from runtime.external_interaction_recovery
                where execution_id = :executionId and recovery_status = 'PENDING'
                """)
            .param("executionId", executionId).query(Integer.class).single();
        assertThat(evidenceCount).isEqualTo(1);
        assertThat(recoveryCount).isEqualTo(1);
        String pinnedSnapshotDigest = jdbcClient.sql("""
                select snapshot_digest from runtime.digital_asset_runtime_snapshot
                where execution_id = :executionId
                """)
            .param("executionId", executionId).query(String.class).single();
        Integer activeRecoveryCount = jdbcClient.sql("""
                select count(*) from runtime.external_interaction_recovery
                where execution_id = :executionId
                  and recovery_status in ('PENDING', 'RETRY_SCHEDULED', 'CLAIMED')
                """)
            .param("executionId", executionId)
            .query(Integer.class)
            .single();
        assertThat(activeRecoveryCount).isEqualTo(1);

        for (int attempt = 0; attempt < 50 && !isRecoveryReconciled(executionId); attempt++) {
            assertThat(recoveryService.processNext("digital-asset-e2e-worker")).isTrue();
        }
        assertThat(isRecoveryReconciled(executionId)).isTrue();

        ReconciledState reconciled = jdbcClient.sql("""
                select re.status as runtime_status, re.connector_status,
                       rr.recovery_status, rr.last_observed_external_status,
                       rr.status_query_evidence_digest
                from runtime.runtime_execution re
                join runtime.external_interaction_recovery rr on rr.execution_id = re.execution_id
                where re.execution_id = :executionId
                """)
            .param("executionId", executionId)
            .query(ReconciledState.class)
            .single();
        assertThat(reconciled.runtimeStatus()).isEqualTo("EXTERNALLY_RECONCILED");
        assertThat(reconciled.connectorStatus()).isEqualTo("ACKNOWLEDGED");
        assertThat(reconciled.recoveryStatus()).isEqualTo("RECONCILED");
        assertThat(reconciled.lastObservedExternalStatus()).isEqualTo("ACKNOWLEDGED");
        assertThat(reconciled.statusQueryEvidenceDigest()).matches("[0-9a-f]{64}");
        SettlementState settlement = jdbcClient.sql("""
                select settlement_status, reconciliation_result
                from runtime.digital_asset_transaction
                where execution_id = :executionId
                """)
            .param("executionId", executionId)
            .query(SettlementState.class)
            .single();
        assertThat(settlement.settlementStatus()).isEqualTo("SETTLED");
        assertThat(settlement.reconciliationResult()).isEqualTo("RECOVERED");
        Integer recoveredEvidence = jdbcClient.sql("""
                select count(*) from runtime.digital_asset_post_execution_evidence
                where execution_id = :executionId and status = 'VERIFIED'
                  and evidence_source_type = 'INDEPENDENT_EXTERNAL'
                  and external_status = 'SETTLED' and receipt_status = 'SUCCESS'
                  and finality_status = 'FINALIZED'
                """)
            .param("executionId", executionId).query(Integer.class).single();
        assertThat(recoveredEvidence).isEqualTo(1);
        assertThat(platformStateStore.externalEffectCount(providerRequestId(executionId))).isEqualTo(1);
        String recoveredSnapshotDigest = jdbcClient.sql("""
                select snapshot_digest from runtime.digital_asset_runtime_snapshot
                where execution_id = :executionId
                """)
            .param("executionId", executionId).query(String.class).single();
        assertThat(recoveredSnapshotDigest).isEqualTo(pinnedSnapshotDigest);
    }

    @Test
    void rejectsStaleLeaseBeforeDigitalAssetEvidenceIsWritten() throws Exception {
        String executionId = submitSentUnknownExecution();
        String recoveryId = recoveryId(executionId);
        String workerId = "stale-evidence-worker";
        OffsetDateTime claimedAt = OffsetDateTime.now();
        var recovery = recoveryPersistence.claimById(
            recoveryId, workerId, "institution_local", Set.of("tokenized_asset_purchase"),
            claimedAt, Duration.ofMinutes(1)
        ).orElseThrow();
        jdbcClient.sql("""
                update runtime.external_interaction_recovery
                set lease_owner = 'replacement-worker'
                where recovery_id = :recoveryId
                """)
            .param("recoveryId", recoveryId)
            .update();

        assertThatThrownBy(() -> reconciliationCoordinator.commit(
            recovery, workerId, acknowledgedStatus(), claimedAt.plusSeconds(1)
        )).isInstanceOf(StaleRecoveryLeaseException.class);

        assertRecoveryWritesWereNotCommitted(executionId, "replacement-worker");
    }

    @Test
    void rollsBackRecoveryStateWhenDigitalAssetEvidenceWriteFails() throws Exception {
        String executionId = submitSentUnknownExecution();
        String recoveryId = recoveryId(executionId);
        String workerId = "rollback-evidence-worker";
        OffsetDateTime claimedAt = OffsetDateTime.now();
        var recovery = recoveryPersistence.claimById(
            recoveryId, workerId, "institution_local", Set.of("tokenized_asset_purchase"),
            claimedAt, Duration.ofMinutes(1)
        ).orElseThrow();
        platformStateStore.removeRecoveryObservation(providerRequestId(executionId));

        assertThatThrownBy(() -> reconciliationCoordinator.commit(
            recovery, workerId, acknowledgedStatus(), claimedAt.plusSeconds(1)
        )).isInstanceOf(ExternalStatusQueryPermanentException.class);

        assertRecoveryWritesWereNotCommitted(executionId, workerId);
    }

    @Test
    void failsWhenIndependentReceiptConfirmsExecutionFailure() throws Exception {
        String response = assetRequest(token(), "customer-100", "asset-execution-failed")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("FAILED"))
            .andExpect(jsonPath("$.connectorStatus").value("ACKNOWLEDGED"))
            .andExpect(jsonPath("$.output.deliveryStatus").value("WITHHELD"))
            .andReturn().getResponse().getContentAsString();
        String executionId = response.replaceAll(".*\\\"executionId\\\":\\\"([^\\\"]+)\\\".*", "$1");

        Integer failedTransaction = jdbcClient.sql("""
                select count(*) from runtime.digital_asset_transaction
                where execution_id = :executionId and settlement_status = 'FAILED'
                  and reconciliation_result = 'WAIT' and external_transaction_id is not null
                  and provider_response_digest is not null
                """)
            .param("executionId", executionId).query(Integer.class).single();
        Integer failedEvidence = jdbcClient.sql("""
                select count(*) from runtime.digital_asset_post_execution_evidence
                where execution_id = :executionId and status = 'FAILED'
                  and evidence_source_type = 'INDEPENDENT_EXTERNAL'
                  and external_status = 'FAILED' and receipt_status = 'FAILED'
                  and finality_status = 'FINALIZED'
                """)
            .param("executionId", executionId).query(Integer.class).single();
        assertThat(failedTransaction).isEqualTo(1);
        assertThat(failedEvidence).isEqualTo(1);
        assertThat(platformStateStore.externalEffectCount(providerRequestId(executionId))).isEqualTo(1);

        mockMvc.perform(get("/v1/runtime/executions/{executionId}/trace", executionId)
                .header("X-ADP-API-Key", "local-dev-api-key"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("FAILED"))
            .andExpect(jsonPath("$.digitalAssetPostExecutionEvidence.status").value("FAILED"))
            .andExpect(jsonPath("$.digitalAssetPostExecutionEvidence.evidenceSourceType")
                .value("INDEPENDENT_EXTERNAL"));
    }

    @Test
    void schedulesRecoveryWhenProviderPayloadReportsTypedSentUnknown() throws Exception {
        String response = assetRequest(token(), "customer-100", "asset-provider-sent-unknown")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("EGRESSING"))
            .andExpect(jsonPath("$.connectorStatus").value("ACKNOWLEDGED"))
            .andReturn().getResponse().getContentAsString();
        String executionId = response.replaceAll(".*\\\"executionId\\\":\\\"([^\\\"]+)\\\".*", "$1");

        Integer evidenceCount = jdbcClient.sql("""
                select count(*) from runtime.digital_asset_post_execution_evidence
                where execution_id = :executionId and status = 'SENT_UNKNOWN'
                  and external_status = 'SENT_UNKNOWN'
                """)
            .param("executionId", executionId).query(Integer.class).single();
        Integer recoveryCount = jdbcClient.sql("""
                select count(*) from runtime.external_interaction_recovery
                where execution_id = :executionId and observed_status = 'SENT_UNKNOWN'
                  and recovery_status = 'PENDING' and retry_disposition = 'RECONCILE_FIRST'
                """)
            .param("executionId", executionId).query(Integer.class).single();

        assertThat(evidenceCount).isEqualTo(1);
        assertThat(recoveryCount).isEqualTo(1);
    }

    private org.springframework.test.web.servlet.ResultActions assetRequest(
        String suffix, String customerId, String assetId
    ) throws Exception {
        return assetRequest(
            suffix, customerId, "wallet-test-001", assetId, "10000"
        );
    }

    private org.springframework.test.web.servlet.ResultActions assetRequest(
        String suffix,
        String customerId,
        String walletAddress,
        String assetId,
        String amount
    ) throws Exception {
        return assetRequest(
            suffix, customerId, approvalReferenceFor(assetId), walletAddress, assetId, amount,
            "beneficiary-local-001"
        );
    }

    private org.springframework.test.web.servlet.ResultActions assetRequest(
        String suffix,
        String customerId,
        String approvedTransactionReference,
        String walletAddress,
        String assetId,
        String amount,
        String beneficiaryReference
    ) throws Exception {
        return mockMvc.perform(post("/v1/runtime/executions")
            .header("X-Request-Id", "req_asset_case_" + suffix)
            .header("X-Trace-Id", "trace_asset_case_" + suffix)
            .header("X-ADP-API-Key", "local-dev-api-key")
            .contentType("application/json")
            .content("""
                {"institutionId":"institution_local","approvalReference":"approval_digital_asset_purchase_v1",
                 "workloadId":"tokenized_asset_purchase","purposeCode":"DIGITAL_ASSET_PURCHASE",
                 "subjectScope":"customer:customer-100","destinationProfileId":"dest_mock_asset_platform_v1",
                 "idempotencyKey":"idem_asset_case_%s","processingContexts":["DIGITAL_ASSET"],
                 "input":{"approvedTransactionReference":"%s","customerId":"%s",
                 "accountId":"acct-100-1","outboundRequest":{"requestedAsset":{
                 "chainId":"eip155:1","assetKind":"FUNGIBLE_TOKEN","assetSymbol":"%s",
                 "assetContractAddress":"0x0000000000000000000000000000000000000001",
                 "operation":"TRANSFER","tokenId":null},"requestedAmount":"%s",
                 "requestedDestination":"%s","requestedBeneficiaryReference":"%s",
                 "regulatoryOutboundData":{}}}}
                """.formatted(
                    suffix, approvedTransactionReference, customerId, assetId, amount, walletAddress,
                    beneficiaryReference
                )));
    }

    private void assertPolicyBlock(
        String label,
        String approvedTransactionReference,
        String walletAddress,
        String assetId,
        String amount,
        String beneficiaryReference,
        String expectedReason
    ) throws Exception {
        String suffix = label + "_" + token();
        assetRequest(
            suffix, "customer-100", approvedTransactionReference, walletAddress, assetId, amount,
            beneficiaryReference
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("BLOCKED"))
            .andExpect(jsonPath("$.finalAction").value("BLOCK"))
            .andExpect(jsonPath("$.connectorStatus").value("NOT_SENT"));

        PolicyGateEvidence evidence = jdbcClient.sql("""
                select pe.profile_version, pe.profile_digest, pe.profile_action, pe.final_action,
                       pe.reason_codes, pe.assertion_source, pe.assertion_version, pe.assertion_digest,
                       (select count(*) from runtime.connector_execution ce
                        where ce.execution_id = re.execution_id) as connector_count
                from runtime.runtime_execution re
                join runtime.execution_pack_policy_evaluation pe on pe.execution_id = re.execution_id
                where re.request_id = :requestId
                """)
            .param("requestId", "req_asset_case_" + suffix)
            .query(PolicyGateEvidence.class)
            .single();
        assertThat(evidence.profileVersion()).isEqualTo("0.3.0");
        assertThat(evidence.profileDigest()).matches("[0-9a-f]{64}");
        assertThat(evidence.profileAction()).isEqualTo("BLOCK");
        assertThat(evidence.finalAction()).isEqualTo("BLOCK");
        assertThat(evidence.reasonCodes()).contains(expectedReason);
        assertThat(evidence.assertionSource()).isNull();
        assertThat(evidence.assertionVersion()).isNull();
        assertThat(evidence.assertionDigest()).isNull();
        assertThat(evidence.connectorCount()).isZero();
    }

    private void assertInvalidInput(String suffix, String amountJson, String regulatoryJson) throws Exception {
        mockMvc.perform(post("/v1/runtime/executions")
                .header("X-Request-Id", "req_asset_invalid_" + suffix)
                .header("X-Trace-Id", "trace_asset_invalid_" + suffix)
                .header("X-ADP-API-Key", "local-dev-api-key")
                .contentType("application/json")
                .content("""
                    {"institutionId":"institution_local","approvalReference":"approval_digital_asset_purchase_v1",
                     "workloadId":"tokenized_asset_purchase","purposeCode":"DIGITAL_ASSET_PURCHASE",
                     "subjectScope":"customer:customer-100","destinationProfileId":"dest_mock_asset_platform_v1",
                     "idempotencyKey":"idem_asset_invalid_%s","processingContexts":["DIGITAL_ASSET"],
                     "input":{"approvedTransactionReference":"approved-tx-local-001","customerId":"customer-100",
                     "accountId":"acct-100-1","outboundRequest":{"requestedAsset":{"chainId":"eip155:1",
                     "assetKind":"FUNGIBLE_TOKEN","assetSymbol":"asset-krw-token-001",
                     "assetContractAddress":"0x0000000000000000000000000000000000000001",
                     "operation":"TRANSFER","tokenId":null},"requestedAmount":%s,
                     "requestedDestination":"wallet-test-001",
                     "requestedBeneficiaryReference":"beneficiary-local-001",
                     "regulatoryOutboundData":%s}}}
                    """.formatted(suffix, amountJson, regulatoryJson)))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.errorCode").value("EXECUTION_PACK_INPUT_REJECTED"));

        assertThat(connectorCount("req_asset_invalid_" + suffix)).isZero();
    }

    private int connectorCount(String requestId) {
        return jdbcClient.sql("""
                select count(*) from runtime.connector_execution ce
                join runtime.runtime_execution re on re.execution_id = ce.execution_id
                where re.request_id = :requestId
                """)
            .param("requestId", requestId)
            .query(Integer.class)
            .single();
    }

    private String providerRequestId(String executionId) {
        return jdbcClient.sql("""
                select provider_request_id from runtime.provider_request where execution_id = :executionId
                """)
            .param("executionId", executionId)
            .query(String.class)
            .single();
    }

    private boolean isRecoveryReconciled(String executionId) {
        return jdbcClient.sql("""
                select recovery_status = 'RECONCILED'
                from runtime.external_interaction_recovery
                where execution_id = :executionId
                """)
            .param("executionId", executionId)
            .query(Boolean.class)
            .single();
    }

    private String approvalReferenceFor(String assetId) {
        return "asset-krw-token-001".equals(assetId)
            ? "approved-tx-local-001"
            : "approved-tx-" + assetId;
    }

    private double terminalTransitions(String status) {
        var counter = meterRegistry.find("adp.runtime.terminal.transition.total")
            .tag("status", status)
            .counter();
        return counter == null ? 0 : counter.count();
    }

    private void assertMismatchCase(String executionId, String severity, String field) {
        MismatchCase mismatch = jdbcClient.sql("""
                select severity, mismatched_fields::text as mismatched_fields,
                       expected_projection_digest, actual_projection_digest, case_status, auto_retry_allowed,
                       (select count(*) from runtime.external_interaction_recovery er
                        where er.execution_id = mc.execution_id) as recovery_count
                from runtime.digital_asset_mismatch_case mc
                where execution_id = :executionId
                """)
            .param("executionId", executionId)
            .query(MismatchCase.class)
            .single();
        assertThat(mismatch.severity()).isEqualTo(severity);
        assertThat(mismatch.mismatchedFields()).contains(field);
        assertThat(mismatch.expectedProjectionDigest()).matches("[0-9a-f]{64}");
        assertThat(mismatch.actualProjectionDigest()).matches("[0-9a-f]{64}");
        assertThat(mismatch.expectedProjectionDigest()).isNotEqualTo(mismatch.actualProjectionDigest());
        assertThat(mismatch.caseStatus()).isEqualTo("OPEN");
        assertThat(mismatch.autoRetryAllowed()).isFalse();
        assertThat(mismatch.recoveryCount()).isZero();
    }

    private String token() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private String submitSentUnknownExecution() throws Exception {
        String response = assetRequest(token(), "customer-100", "asset-sent-unknown")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("EGRESSING"))
            .andReturn().getResponse().getContentAsString();
        return response.replaceAll(".*\\\"executionId\\\":\\\"([^\\\"]+)\\\".*", "$1");
    }

    private String recoveryId(String executionId) {
        return jdbcClient.sql("""
                select recovery_id from runtime.external_interaction_recovery
                where execution_id = :executionId
                """)
            .param("executionId", executionId)
            .query(String.class)
            .single();
    }

    private ExternalStatusQueryResult acknowledgedStatus() {
        return new ExternalStatusQueryResult(ConnectorStatus.ACKNOWLEDGED, "a".repeat(64));
    }

    private void assertRecoveryWritesWereNotCommitted(String executionId, String expectedLeaseOwner) {
        RecoveryAtomicState state = jdbcClient.sql("""
                select re.status as runtime_status, re.connector_status,
                       rr.recovery_status, rr.lease_owner,
                       dat.settlement_status, dat.reconciliation_result,
                       (select count(*) from runtime.digital_asset_post_execution_evidence pe
                        where pe.execution_id = re.execution_id) as post_evidence_count
                from runtime.runtime_execution re
                join runtime.external_interaction_recovery rr on rr.execution_id = re.execution_id
                join runtime.digital_asset_transaction dat on dat.execution_id = re.execution_id
                where re.execution_id = :executionId
                """)
            .param("executionId", executionId)
            .query(RecoveryAtomicState.class)
            .single();
        assertThat(state.runtimeStatus()).isEqualTo("EGRESSING");
        assertThat(state.connectorStatus()).isEqualTo("SENT_UNKNOWN");
        assertThat(state.recoveryStatus()).isEqualTo("CLAIMED");
        assertThat(state.leaseOwner()).isEqualTo(expectedLeaseOwner);
        assertThat(state.settlementStatus()).isEqualTo("SENT_UNKNOWN");
        assertThat(state.reconciliationResult()).isEqualTo("WAIT");
        assertThat(state.postEvidenceCount()).isZero();
    }

    private record ReconciledState(
        String runtimeStatus,
        String connectorStatus,
        String recoveryStatus,
        String lastObservedExternalStatus,
        String statusQueryEvidenceDigest
    ) {
    }

    private record SettlementState(String settlementStatus, String reconciliationResult) {
    }

    private record RecoveryAtomicState(
        String runtimeStatus,
        String connectorStatus,
        String recoveryStatus,
        String leaseOwner,
        String settlementStatus,
        String reconciliationResult,
        int postEvidenceCount
    ) {
    }

    private record MismatchCase(
        String severity,
        String mismatchedFields,
        String expectedProjectionDigest,
        String actualProjectionDigest,
        String caseStatus,
        boolean autoRetryAllowed,
        int recoveryCount
    ) {
    }

    private record PolicyGateEvidence(
        String profileVersion,
        String profileDigest,
        String profileAction,
        String finalAction,
        String reasonCodes,
        String assertionSource,
        String assertionVersion,
        String assertionDigest,
        int connectorCount
    ) {
    }
}
