package com.adp.gateway.digitalasset.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.Map;

import com.adp.gateway.context.application.CanonicalValueHasher;
import com.adp.gateway.digitalasset.domain.DigitalAssetAmount;
import com.adp.gateway.digitalasset.domain.DigitalAssetExternalStatus;
import com.adp.gateway.digitalasset.domain.DigitalAssetFinalityStatus;
import com.adp.gateway.digitalasset.domain.DigitalAssetKind;
import com.adp.gateway.digitalasset.domain.DigitalAssetMismatchField;
import com.adp.gateway.digitalasset.domain.DigitalAssetOperation;
import com.adp.gateway.digitalasset.domain.DigitalAssetProviderStatus;
import com.adp.gateway.digitalasset.domain.DigitalAssetReceiptStatus;
import com.adp.gateway.digitalasset.domain.DigitalAssetReconciliationResult;
import com.adp.gateway.digitalasset.domain.ExternalExecutionResult;
import org.junit.jupiter.api.Test;

class DigitalAssetReconciliationEvaluatorTests {
    private static final OffsetDateTime EXECUTED_AT = OffsetDateTime.parse("2026-09-08T00:00:00Z");
    private final DigitalAssetReconciliationEvaluator evaluator =
        new DigitalAssetReconciliationEvaluator(new CanonicalValueHasher());

    @Test
    void classifiesCriticalFieldMismatch() {
        var assessment = evaluator.evaluate(request("wallet-1", "ASSET", "100"), result(
            "wallet-1", "ASSET", "101", DigitalAssetExternalStatus.SETTLED
        ));

        assertThat(assessment.result()).isEqualTo(DigitalAssetReconciliationResult.CRITICAL_MISMATCH);
        assertThat(assessment.mismatchedFields()).containsExactly(DigitalAssetMismatchField.AMOUNT);
    }

    @Test
    void keepsNonFinalExecutionWaitingEvenWhenPayloadDiffers() {
        var assessment = evaluator.evaluate(request("wallet-1", "ASSET", "100"), result(
            "wallet-2", "ASSET", "100", DigitalAssetExternalStatus.SETTLING
        ));

        assertThat(assessment.result()).isEqualTo(DigitalAssetReconciliationResult.WAIT);
        assertThat(assessment.mismatchedFields()).isEmpty();
    }

    @Test
    void comparesTheCompleteCanonicalExecutionTuple() {
        var assessment = evaluator.evaluate(request("wallet-1", "ASSET", "100"), result(
            "wallet-1", "OTHER", "100", DigitalAssetExternalStatus.SETTLED
        ));

        assertThat(assessment.mismatchedFields()).containsExactly(DigitalAssetMismatchField.ASSET_SYMBOL);
    }

    @Test
    void digestsCorrelationMismatchWithoutRetainingRawIdentifiers() {
        var assessment = evaluator.criticalCorrelationMismatch("expected-request", "actual-request");

        assertThat(assessment.result()).isEqualTo(DigitalAssetReconciliationResult.CRITICAL_MISMATCH);
        assertThat(assessment.mismatchedFields()).containsExactly(DigitalAssetMismatchField.EXTERNAL_REQUEST_ID);
        assertThat(assessment.expectedProjectionDigest()).matches("[0-9a-f]{64}");
        assertThat(assessment.actualProjectionDigest()).matches("[0-9a-f]{64}");
        assertThat(assessment.expectedProjectionDigest()).isNotEqualTo(assessment.actualProjectionDigest());
    }

    private Map<String, Object> request(String recipient, String symbol, String amount) {
        return Map.of("transaction", Map.of(
            "chainId", "eip155:1",
            "recipientAddress", recipient,
            "assetKind", "FUNGIBLE_TOKEN",
            "assetSymbol", symbol,
            "assetContractAddress", "0x0000000000000000000000000000000000000001",
            "amount", amount,
            "operation", "TRANSFER"
        ));
    }

    private ExternalExecutionResult result(
        String recipient,
        String symbol,
        String amount,
        DigitalAssetExternalStatus status
    ) {
        boolean settled = status == DigitalAssetExternalStatus.SETTLED;
        return new ExternalExecutionResult(
            "request-1", "external-1", settled ? "0xabc" : null, status,
            DigitalAssetProviderStatus.ACKNOWLEDGED,
            settled ? DigitalAssetReceiptStatus.SUCCESS : DigitalAssetReceiptStatus.PENDING,
            settled ? DigitalAssetFinalityStatus.FINALIZED : DigitalAssetFinalityStatus.UNCONFIRMED,
            "eip155:1", recipient, DigitalAssetKind.FUNGIBLE_TOKEN, symbol,
            "0x0000000000000000000000000000000000000001", DigitalAssetAmount.from(amount),
            DigitalAssetAmount.from("0"), DigitalAssetOperation.TRANSFER, null, "transfer-1", null,
            EXECUTED_AT, settled ? EXECUTED_AT.plusMinutes(1) : null, "a".repeat(64)
        );
    }
}
