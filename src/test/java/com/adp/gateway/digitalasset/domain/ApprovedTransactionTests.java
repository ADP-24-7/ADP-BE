package com.adp.gateway.digitalasset.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;

class ApprovedTransactionTests {

    @Test
    void rejectsDigestThatCannotBePersistedAsSha256Evidence() {
        assertThatThrownBy(() -> new ApprovedTransaction(
            "approved-tx-001", "0.3.0", "not-a-digest", "institution-a", "subject-digest-a",
            "tokenized_asset_purchase", "DIGITAL_ASSET_PURCHASE", "policy-snapshot-001",
            new DigitalAssetDescriptor(
                "eip155:1", DigitalAssetKind.FUNGIBLE_TOKEN, "ASSET",
                "0x0000000000000000000000000000000000000001", DigitalAssetOperation.TRANSFER, null
            ),
            null, DigitalAssetAmount.from("1000"), "dest-001", "wallet-001", "beneficiary-001",
            OffsetDateTime.parse("2026-01-01T00:00:00Z"),
            OffsetDateTime.parse("2027-01-01T00:00:00Z")
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("digest must be a lowercase SHA-256 hex value");
    }

    @Test
    void rejectsFractionalAndNegativeAtomicAmounts() {
        assertThatThrownBy(() -> DigitalAssetAmount.from("1.5"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DigitalAssetAmount.from("-1"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void bindsTheServerOwnedApprovalToTheCompleteOutboundIdentity() {
        DigitalAssetDescriptor asset = new DigitalAssetDescriptor(
            "eip155:1", DigitalAssetKind.FUNGIBLE_TOKEN, "ASSET",
            "0x0000000000000000000000000000000000000001", DigitalAssetOperation.TRANSFER, null
        );
        ApprovedTransaction approved = new ApprovedTransaction(
            "approved-tx-001", "0.3.0", "a".repeat(64), "institution-a", "subject-digest-a",
            "tokenized_asset_purchase", "DIGITAL_ASSET_PURCHASE", "policy-snapshot-001", asset,
            null, DigitalAssetAmount.from("1000"), "dest-001", "wallet-001", "beneficiary-001",
            OffsetDateTime.parse("2026-01-01T00:00:00Z"), OffsetDateTime.parse("2027-01-01T00:00:00Z")
        );
        OutboundRequest allowed = new OutboundRequest(
            asset, DigitalAssetAmount.from("1000"), "wallet-001", "beneficiary-001",
            OffsetDateTime.parse("2026-09-08T00:00:00Z"), java.util.Map.of(), "dest-001", "idem-001"
        );
        OutboundRequest changedRecipient = new OutboundRequest(
            asset, DigitalAssetAmount.from("1000"), "wallet-other", "beneficiary-001",
            OffsetDateTime.parse("2026-09-08T00:00:00Z"), java.util.Map.of(), "dest-001", "idem-001"
        );

        assertThat(approved.permits(allowed)).isTrue();
        assertThat(approved.permits(changedRecipient)).isFalse();
    }
}
