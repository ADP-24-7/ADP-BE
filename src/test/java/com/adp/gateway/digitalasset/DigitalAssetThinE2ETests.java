package com.adp.gateway.digitalasset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

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
class DigitalAssetThinE2ETests {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void executesTokenizedAssetPurchaseWithSettlementAndReconciliationEvidence() throws Exception {
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
    void keepsRuntimeEgressingWhileSettlementIsNotFinal() throws Exception {
        assetRequest(token(), "customer-100", "asset-settling")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("EGRESSING"))
            .andExpect(jsonPath("$.output.deliveryStatus").value("WITHHELD"));
    }

    @Test
    void routesCriticalSettlementMismatchToReview() throws Exception {
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
    void persistsSentUnknownWithoutSettlementIdentifiersAndSchedulesRecovery() throws Exception {
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
        jdbcClient.sql("delete from runtime.external_interaction_recovery where execution_id = :executionId")
            .param("executionId", executionId).update();
    }

    private org.springframework.test.web.servlet.ResultActions assetRequest(
        String suffix, String customerId, String assetId
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
                 "input":{"customerId":"%s","accountId":"acct-100-1","walletAddress":"wallet-test-001",
                 "assetId":"%s","amount":10000,"kycStatus":"VERIFIED","amlStatus":"PASSED","walletVerified":true}}
                """.formatted(suffix, customerId, assetId)));
    }

    private String token() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
