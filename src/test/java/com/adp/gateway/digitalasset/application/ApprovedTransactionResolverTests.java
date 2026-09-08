package com.adp.gateway.digitalasset.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import com.adp.gateway.digitalasset.domain.ApprovedTransactionReference;
import com.adp.gateway.digitalasset.domain.ApprovedTransaction;
import com.adp.gateway.digitalasset.domain.DigitalAssetAmount;
import com.adp.gateway.digitalasset.domain.DigitalAssetDescriptor;
import com.adp.gateway.digitalasset.domain.DigitalAssetKind;
import com.adp.gateway.digitalasset.domain.DigitalAssetOperation;
import org.junit.jupiter.api.Test;

class ApprovedTransactionResolverTests {

    @Test
    void resolvesServerOwnedSnapshotWithTheCompleteRuntimeScope() {
        AtomicReference<ApprovedTransactionLookup> captured = new AtomicReference<>();
        ApprovedTransaction snapshot = snapshot();
        ApprovedTransactionResolver resolver = new ApprovedTransactionResolver(List.of(lookup -> {
            captured.set(lookup);
            return Optional.of(snapshot);
        }));
        ApprovedTransactionLookup lookup = lookup("institution-a", "subject-digest-a");

        assertThat(resolver.resolve(lookup)).isSameAs(snapshot);
        assertThat(captured.get()).isEqualTo(lookup);
    }

    @Test
    void failsClosedWhenNoPortFindsTheReferenceInsideTheRuntimeScope() {
        ApprovedTransactionResolver resolver = new ApprovedTransactionResolver(List.of(lookup -> Optional.empty()));

        assertThatThrownBy(() -> resolver.resolve(lookup("institution-b", "subject-digest-b")))
            .isInstanceOf(ApprovedTransactionUnavailableException.class);
    }

    @Test
    void rejectsSnapshotReturnedFromAnotherScopeEvenWhenThePortReturnsIt() {
        ApprovedTransactionResolver resolver = new ApprovedTransactionResolver(List.of(lookup -> Optional.of(snapshot())));

        assertThatThrownBy(() -> resolver.resolve(lookup("institution-other", "subject-digest-a")))
            .isInstanceOf(ApprovedTransactionUnavailableException.class);
    }

    @Test
    void rejectsMultipleApprovalAuthoritiesInsteadOfSelectingOneByBeanOrder() {
        ApprovedTransactionPort first = lookup -> Optional.of(snapshot());
        ApprovedTransactionPort second = lookup -> Optional.of(snapshot());

        assertThatThrownBy(() -> new ApprovedTransactionResolver(List.of(first, second)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Multiple approved transaction authorities are configured");
    }

    private ApprovedTransactionLookup lookup(String institutionId, String subjectDigest) {
        return new ApprovedTransactionLookup(
            new ApprovedTransactionReference("approved-tx-001"), institutionId, subjectDigest,
            "tokenized_asset_purchase", "DIGITAL_ASSET_PURCHASE"
        );
    }

    private ApprovedTransaction snapshot() {
        return new ApprovedTransaction(
            "approved-tx-001", "0.2.0", "a".repeat(64), "institution-a", "subject-digest-a",
            "tokenized_asset_purchase", "DIGITAL_ASSET_PURCHASE", "policy-snapshot-001",
            new DigitalAssetDescriptor(
                "eip155:1", DigitalAssetKind.FUNGIBLE_TOKEN, "ASSET",
                "0x0000000000000000000000000000000000000001", DigitalAssetOperation.TRANSFER, null
            ),
            null, DigitalAssetAmount.from("1000"), "dest-001", "wallet-001", "beneficiary-001",
            OffsetDateTime.parse("2026-01-01T00:00:00Z"),
            OffsetDateTime.parse("2027-01-01T00:00:00Z")
        );
    }
}
