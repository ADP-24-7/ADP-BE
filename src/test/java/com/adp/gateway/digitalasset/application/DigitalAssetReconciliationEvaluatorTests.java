package com.adp.gateway.digitalasset.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import com.adp.gateway.context.application.CanonicalValueHasher;
import org.junit.jupiter.api.Test;

class DigitalAssetReconciliationEvaluatorTests {
    private final DigitalAssetReconciliationEvaluator evaluator =
        new DigitalAssetReconciliationEvaluator(new CanonicalValueHasher());

    @Test
    void classifiesCriticalFieldMismatch() {
        var assessment = evaluator.evaluate(
            request(transaction("wallet-1", "asset-1", "100", "PASSED")),
            response(transaction("wallet-1", "asset-1", "101", "PASSED")),
            "SETTLED"
        );

        assertThat(assessment.result()).isEqualTo("CRITICAL_MISMATCH");
        assertThat(assessment.mismatchedFields()).containsExactly("amount");
    }

    @Test
    void classifiesNonCriticalFieldMismatch() {
        var assessment = evaluator.evaluate(
            request(transaction("wallet-1", "asset-1", "100", "PASSED")),
            response(transaction("wallet-1", "asset-1", "100", "REVIEW")),
            "SETTLED"
        );

        assertThat(assessment.result()).isEqualTo("MISMATCH");
        assertThat(assessment.mismatchedFields()).containsExactly("kycStatus");
    }

    @Test
    void keepsNonFinalSettlementWaitingEvenWhenPayloadDiffers() {
        var assessment = evaluator.evaluate(
            request(transaction("wallet-1", "asset-1", "100", "PASSED")),
            response(transaction("wallet-2", "asset-1", "100", "PASSED")),
            "SETTLING"
        );

        assertThat(assessment.result()).isEqualTo("WAIT");
        assertThat(assessment.mismatchedFields()).isEmpty();
    }

    @Test
    void digestsCorrelationMismatchWithoutRetainingRawIdentifiers() {
        var assessment = evaluator.criticalCorrelationMismatch("expected-request", "actual-request");

        assertThat(assessment.result()).isEqualTo("CRITICAL_MISMATCH");
        assertThat(assessment.mismatchedFields()).containsExactly("externalRequestId");
        assertThat(assessment.expectedDigest()).matches("[0-9a-f]{64}");
        assertThat(assessment.actualDigest()).matches("[0-9a-f]{64}");
        assertThat(assessment.expectedDigest()).isNotEqualTo(assessment.actualDigest());
    }

    private Map<String, Object> request(Map<String, Object> transaction) {
        return Map.of("transaction", transaction);
    }

    private Map<String, Object> response(Map<String, Object> transaction) {
        return Map.of("settledTransaction", transaction);
    }

    private Map<String, Object> transaction(String wallet, String asset, String amount, String kyc) {
        return Map.of("walletAddress", wallet, "assetId", asset, "amount", amount, "kycStatus", kyc);
    }
}
