package com.adp.gateway.digitalasset.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

}
