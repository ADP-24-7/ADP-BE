package com.adp.gateway.digitalasset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

import com.adp.gateway.recovery.application.ExternalInteractionRecoveryService;

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
                        "customerId":"customer-100",
                        "accountId":"acct-100-1",
                        "walletAddress":"wallet-test-001",
                        "assetId":"asset-krw-token-001",
                        "amount":10000,
                        "kycStatus":"VERIFIED",
                        "amlStatus":"PASSED",
                        "walletVerified":true
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
                  and tf.field_path in ('$.input.walletAddress', '$.input.assetId', '$.input.amount')
                  and tf.strategy = 'KEEP'
                """)
            .param("executionId", executionId).query(Integer.class).single();
        assertThat(exactFields).isEqualTo(3);

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
        assertThat(terminalTransitions("COMPLETED")).isEqualTo(completedBefore + 1);

        mockMvc.perform(get("/v1/runtime/executions/{executionId}/trace", executionId)
                .header("X-ADP-API-Key", "local-dev-api-key"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.evidence.destinationProfileId").value("dest_mock_asset_platform_v1"))
            .andExpect(jsonPath("$.stages[?(@.stage == 'CONNECTOR')].status").value("COMPLETED"));

        assertThat(response)
            .doesNotContain("customer-100")
            .doesNotContain("acct-100-1")
            .doesNotContain("wallet-test-001");
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
    void routesDigitalAssetPolicyViolationsToReviewBeforeConnector() throws Exception {
        assertPolicyReview("kyc", "wallet-kyc-pending", "10000", "VERIFIED", "PASSED", true,
            "DIGITAL_ASSET_KYC_REVIEW_REQUIRED");
        assertPolicyReview("aml", "wallet-aml-review", "10000", "VERIFIED", "PASSED", true,
            "DIGITAL_ASSET_AML_REVIEW_REQUIRED");
        assertPolicyReview("wallet", "wallet-unverified", "10000", "VERIFIED", "PASSED", true,
            "DIGITAL_ASSET_WALLET_REVIEW_REQUIRED");
        assertPolicyReview("amount", "wallet-test-001", "10000001", "VERIFIED", "PASSED", true,
            "DIGITAL_ASSET_AMOUNT_LIMIT_REVIEW_REQUIRED");
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
        assertThat(terminalTransitions("REVIEW_REQUIRED")).isEqualTo(reviewRequiredBefore + 1);
    }

    @Test
    void rejectsProviderResponseFromAnotherExternalRequest() throws Exception {
        String response = assetRequest(token(), "customer-100", "asset-correlation-mismatch")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("REVIEW_REQUIRED"))
            .andExpect(jsonPath("$.output.deliveryStatus").value("WITHHELD"))
            .andReturn().getResponse().getContentAsString();
        String executionId = response.replaceAll(".*\\\"executionId\\\":\\\"([^\\\"]+)\\\".*", "$1");
        Integer evidenceCount = jdbcClient.sql("""
                select count(*) from runtime.digital_asset_transaction where execution_id = :executionId
                """)
            .param("executionId", executionId).query(Integer.class).single();
        assertThat(evidenceCount).isZero();
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
        Integer activeRecoveryCount = jdbcClient.sql("""
                select count(*) from runtime.external_interaction_recovery
                where recovery_status in ('PENDING', 'RETRY_SCHEDULED', 'CLAIMED')
                """)
            .query(Integer.class)
            .single();
        assertThat(activeRecoveryCount).isEqualTo(1);

        assertThat(recoveryService.processNext("digital-asset-e2e-worker")).isTrue();

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
        assertThat(settlement.settlementStatus()).isEqualTo("SENT_UNKNOWN");
        assertThat(settlement.reconciliationResult()).isEqualTo("WAIT");
    }

    private org.springframework.test.web.servlet.ResultActions assetRequest(
        String suffix, String customerId, String assetId
    ) throws Exception {
        return assetRequest(
            suffix, customerId, "wallet-test-001", assetId, "10000", "VERIFIED", "PASSED", true
        );
    }

    private org.springframework.test.web.servlet.ResultActions assetRequest(
        String suffix,
        String customerId,
        String walletAddress,
        String assetId,
        String amount,
        String kycStatus,
        String amlStatus,
        boolean walletVerified
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
                 "input":{"customerId":"%s","accountId":"acct-100-1","walletAddress":"%s",
                 "assetId":"%s","amount":%s,"kycStatus":"%s","amlStatus":"%s","walletVerified":%s}}
                """.formatted(
                    suffix, customerId, walletAddress, assetId, amount, kycStatus, amlStatus, walletVerified
                )));
    }

    private void assertPolicyReview(
        String label,
        String walletAddress,
        String amount,
        String kycStatus,
        String amlStatus,
        boolean walletVerified,
        String expectedReason
    ) throws Exception {
        String suffix = label + "_" + token();
        assetRequest(
            suffix, "customer-100", walletAddress, "asset-krw-token-001",
            amount, kycStatus, amlStatus, walletVerified
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("REVIEW_REQUIRED"))
            .andExpect(jsonPath("$.finalAction").value("REVIEW"))
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
        assertThat(evidence.profileVersion()).isEqualTo("1.0.0");
        assertThat(evidence.profileDigest()).matches("[0-9a-f]{64}");
        assertThat(evidence.profileAction()).isEqualTo("REVIEW");
        assertThat(evidence.finalAction()).isEqualTo("REVIEW");
        assertThat(evidence.reasonCodes()).contains(expectedReason);
        assertThat(evidence.assertionSource()).isEqualTo("BANK_COMPLIANCE_FIXTURE");
        assertThat(evidence.assertionVersion()).isEqualTo("1.0.0");
        assertThat(evidence.assertionDigest()).matches("[0-9a-f]{64}");
        assertThat(evidence.connectorCount()).isZero();
    }

    private double terminalTransitions(String status) {
        var counter = meterRegistry.find("adp.runtime.terminal.transition.total")
            .tag("status", status)
            .counter();
        return counter == null ? 0 : counter.count();
    }

    private String token() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
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
