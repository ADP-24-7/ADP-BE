package com.adp.gateway.digitalasset.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import com.adp.gateway.context.application.CanonicalValueHasher;
import com.adp.gateway.digitalasset.domain.DigitalAssetMismatchField;
import com.adp.gateway.digitalasset.domain.DigitalAssetReconciliationResult;
import org.junit.jupiter.api.Test;

class DigitalAssetReconciliationEvaluatorTests {
    private final DigitalAssetReconciliationEvaluator evaluator =
        new DigitalAssetReconciliationEvaluator(new CanonicalValueHasher());

    @Test
    void classifiesCriticalFieldMismatch() {
        var assessment = evaluator.evaluate(
            request(transaction("wallet-1", "asset-1", "100")),
            response(transaction("wallet-1", "asset-1", "101")),
            "SETTLED"
        );

        assertThat(assessment.result()).isEqualTo(DigitalAssetReconciliationResult.CRITICAL_MISMATCH);
        assertThat(assessment.mismatchedFields()).containsExactly(DigitalAssetMismatchField.AMOUNT);
    }

    @Test
    void treatsLegacyEligibilityFieldsFromProviderAsUnexpected() {
        var actual = new java.util.HashMap<String, Object>(transaction("wallet-1", "asset-1", "100"));
        actual.put("kycStatus", "REVIEW");
        var assessment = evaluator.evaluate(
            request(transaction("wallet-1", "asset-1", "100")),
            response(actual),
            "SETTLED"
        );

        assertThat(assessment.result()).isEqualTo(DigitalAssetReconciliationResult.CRITICAL_MISMATCH);
        assertThat(assessment.mismatchedFields()).containsExactly(DigitalAssetMismatchField.UNEXPECTED_FIELD);
    }

    @Test
    void keepsNonFinalSettlementWaitingEvenWhenPayloadDiffers() {
        var assessment = evaluator.evaluate(
            request(transaction("wallet-1", "asset-1", "100")),
            response(transaction("wallet-2", "asset-1", "100")),
            "SETTLING"
        );

        assertThat(assessment.result()).isEqualTo(DigitalAssetReconciliationResult.WAIT);
        assertThat(assessment.mismatchedFields()).isEmpty();
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

    @Test
    void normalizesUntrustedProviderKeyToServerOwnedLabel() {
        var expected = transaction("wallet-1", "asset-1", "100");
        var actual = new java.util.HashMap<String, Object>(expected);
        actual.put("customer-100-sensitive-value", "unexpected");

        var assessment = evaluator.evaluate(request(expected), response(actual), "SETTLED");

        assertThat(assessment.result()).isEqualTo(DigitalAssetReconciliationResult.CRITICAL_MISMATCH);
        assertThat(assessment.mismatchedFields()).containsExactly(DigitalAssetMismatchField.UNEXPECTED_FIELD);
        assertThat(assessment.mismatchedFields().toString()).doesNotContain("customer-100-sensitive-value");
    }

    private Map<String, Object> request(Map<String, Object> transaction) {
        return Map.of("transaction", transaction);
    }

    private Map<String, Object> response(Map<String, Object> transaction) {
        return Map.of("settledTransaction", transaction);
    }

    private Map<String, Object> transaction(String wallet, String asset, String amount) {
        return Map.of("walletAddress", wallet, "assetId", asset, "amount", amount);
    }
}
