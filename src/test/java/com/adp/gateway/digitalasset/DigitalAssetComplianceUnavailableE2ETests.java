package com.adp.gateway.digitalasset;

import static org.assertj.core.api.Assertions.assertThat;
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
    "adp.mock-runtime.enabled=true",
    "adp.digital-asset.compliance-fixture.enabled=false"
})
@AutoConfigureMockMvc
class DigitalAssetComplianceUnavailableE2ETests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void bootsButBlocksDigitalAssetExecutionBeforeConnectorWhenComplianceIsUnavailable() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String idempotencyKey = "idem_asset_no_compliance_" + suffix;

        mockMvc.perform(post("/v1/runtime/executions")
                .header("X-Request-Id", "req_asset_no_compliance_" + suffix)
                .header("X-Trace-Id", "trace_asset_no_compliance_" + suffix)
                .header("X-ADP-API-Key", "local-dev-api-key")
                .contentType("application/json")
                .content("""
                    {"institutionId":"institution_local","approvalReference":"approval_digital_asset_purchase_v1",
                     "workloadId":"tokenized_asset_purchase","purposeCode":"DIGITAL_ASSET_PURCHASE",
                     "subjectScope":"customer:customer-100","destinationProfileId":"dest_mock_asset_platform_v1",
                     "idempotencyKey":"%s","processingContexts":["DIGITAL_ASSET"],
                     "input":{"customerId":"customer-100","accountId":"acct-100-1",
                     "walletAddress":"wallet-test-001","assetId":"asset-krw-token-001","amount":10000}}
                    """.formatted(idempotencyKey)))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.reasonCode").value("DIGITAL_ASSET_COMPLIANCE_CONTEXT_NOT_CONFIGURED"));

        ExecutionState state = jdbcClient.sql("""
                select re.status,
                       (select count(*) from runtime.connector_execution ce
                        where ce.execution_id = re.execution_id) as connector_count
                from runtime.runtime_execution re
                where re.idempotency_key = :idempotencyKey
                """)
            .param("idempotencyKey", idempotencyKey)
            .query(ExecutionState.class)
            .single();

        assertThat(state.status()).isEqualTo("BLOCKED");
        assertThat(state.connectorCount()).isZero();
    }

    private record ExecutionState(String status, int connectorCount) {
    }
}
