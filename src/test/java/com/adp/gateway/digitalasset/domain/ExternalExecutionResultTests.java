package com.adp.gateway.digitalasset.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class ExternalExecutionResultTests {

    @Test
    void acceptsFinalSuccessOnlyWithReceiptAndFinalityEvidence() {
        ExternalExecutionResult result = ExternalExecutionResult.from(settledResult(), "a".repeat(64));

        assertThat(result.isFinalSuccess()).isTrue();
    }

    @Test
    void rejectsTransactionHashWithoutSuccessfulReceiptEvidence() {
        Map<String, Object> source = settledResult();
        source.put("receiptStatus", "PENDING");

        assertThatThrownBy(() -> ExternalExecutionResult.from(source, "a".repeat(64)))
            .hasMessage("DIGITAL_ASSET_FINAL_EXECUTION_EVIDENCE_INCOMPLETE");
    }

    @Test
    void rejectsSettledTokenWithoutContractAddress() {
        Map<String, Object> source = settledResult();
        source.put("executedAssetContractAddress", null);

        assertThatThrownBy(() -> ExternalExecutionResult.from(source, "a".repeat(64)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsUnknownProviderResultField() {
        Map<String, Object> source = settledResult();
        source.put("providerRawIdentifier", "sensitive-value");

        assertThatThrownBy(() -> ExternalExecutionResult.from(source, "a".repeat(64)))
            .hasMessage("DIGITAL_ASSET_EXTERNAL_RESULT_SCHEMA_MISMATCH");
    }

    private Map<String, Object> settledResult() {
        Map<String, Object> source = new HashMap<>();
        source.put("externalRequestId", "request-001");
        source.put("externalReference", "external-001");
        source.put("transactionHash", "0xabc");
        source.put("externalStatus", "SETTLED");
        source.put("providerStatus", "ACKNOWLEDGED");
        source.put("receiptStatus", "SUCCESS");
        source.put("finalityStatus", "FINALIZED");
        source.put("executedChainId", "eip155:1");
        source.put("executedRecipientAddress", "wallet-001");
        source.put("executedAssetKind", "FUNGIBLE_TOKEN");
        source.put("executedAssetSymbol", "ASSET");
        source.put("executedAssetContractAddress", "0x0000000000000000000000000000000000000001");
        source.put("executedAmount", "10000");
        source.put("nativeValue", "0");
        source.put("operation", "TRANSFER");
        source.put("tokenId", null);
        source.put("tokenTransferEvidenceRef", "transfer-001");
        source.put("internalTraceEvidenceRef", null);
        source.put("executedAt", "2026-09-08T00:00:00Z");
        source.put("finalizedAt", "2026-09-08T00:01:00Z");
        return source;
    }
}
