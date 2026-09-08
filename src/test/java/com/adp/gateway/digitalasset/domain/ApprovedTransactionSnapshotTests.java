package com.adp.gateway.digitalasset.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;

class ApprovedTransactionSnapshotTests {

    @Test
    void rejectsDigestThatCannotBePersistedAsSha256Evidence() {
        assertThatThrownBy(() -> new ApprovedTransactionSnapshot(
            "approved-tx-001", "0.2.0", "not-a-digest", "institution-a", "subject-digest-a",
            "tokenized_asset_purchase", "DIGITAL_ASSET_PURCHASE", "asset-001",
            new BigDecimal("1000"), "dest-001", "wallet-001", "beneficiary-001",
            OffsetDateTime.parse("2026-01-01T00:00:00Z"),
            OffsetDateTime.parse("2027-01-01T00:00:00Z")
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("digest must be a lowercase SHA-256 hex value");
    }
}
