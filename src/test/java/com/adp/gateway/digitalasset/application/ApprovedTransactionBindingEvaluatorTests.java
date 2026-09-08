package com.adp.gateway.digitalasset.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.Map;

import com.adp.gateway.common.error.ReasonCode;
import com.adp.gateway.digitalasset.domain.ApprovedTransaction;
import com.adp.gateway.digitalasset.domain.DigitalAssetAmount;
import com.adp.gateway.digitalasset.domain.DigitalAssetDescriptor;
import com.adp.gateway.digitalasset.domain.DigitalAssetKind;
import com.adp.gateway.digitalasset.domain.DigitalAssetOperation;
import com.adp.gateway.digitalasset.domain.OutboundRequest;
import org.junit.jupiter.api.Test;

class ApprovedTransactionBindingEvaluatorTests {
    private final ApprovedTransactionBindingEvaluator evaluator = new ApprovedTransactionBindingEvaluator();

    @Test
    void bindsTheServerOwnedApprovalToTheCompleteOutboundIdentity() {
        DigitalAssetDescriptor asset = asset();
        ApprovedTransaction approved = approved(asset);

        assertThat(evaluator.evaluate(approved, request(asset, "wallet-001", "1000"))).isEmpty();
        assertThat(evaluator.evaluate(approved, request(asset, "wallet-other", "1000")))
            .containsExactly(ReasonCode.DIGITAL_ASSET_APPROVED_DESTINATION_MISMATCH);
        assertThat(evaluator.evaluate(approved, request(asset, "wallet-001", "1001")))
            .containsExactly(ReasonCode.DIGITAL_ASSET_APPROVED_AMOUNT_EXCEEDED);
    }

    private ApprovedTransaction approved(DigitalAssetDescriptor asset) {
        return new ApprovedTransaction(
            "approved-tx-001", "0.3.0", "a".repeat(64), "institution-a", "subject-digest-a",
            "tokenized_asset_purchase", "DIGITAL_ASSET_PURCHASE", "policy-snapshot-001", asset,
            DigitalAssetAmount.from("1000"), null, "dest-001", "wallet-001", "beneficiary-001",
            OffsetDateTime.parse("2026-01-01T00:00:00Z"), OffsetDateTime.parse("2027-01-01T00:00:00Z")
        );
    }

    private OutboundRequest request(DigitalAssetDescriptor asset, String destination, String amount) {
        return new OutboundRequest(
            asset, DigitalAssetAmount.from(amount), destination, "beneficiary-001",
            OffsetDateTime.parse("2026-09-08T00:00:00Z"), Map.of(), "dest-001", "idem-001"
        );
    }

    private DigitalAssetDescriptor asset() {
        return new DigitalAssetDescriptor(
            "eip155:1", DigitalAssetKind.FUNGIBLE_TOKEN, "ASSET",
            "0x0000000000000000000000000000000000000001", DigitalAssetOperation.TRANSFER, null
        );
    }
}
