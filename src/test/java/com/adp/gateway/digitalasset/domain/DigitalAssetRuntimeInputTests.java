package com.adp.gateway.digitalasset.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

import com.adp.gateway.context.application.ExecutionPackRequestScope;
import org.junit.jupiter.api.Test;

class DigitalAssetRuntimeInputTests {

    @Test
    void acceptsPositiveAtomicAmountOnlyAsAString() {
        assertThat(DigitalAssetRuntimeInput.from(validInput(), scope()).outboundRequest().requestedAmount().toString())
            .isEqualTo("10000");

        Map<String, Object> numeric = validInput();
        outbound(numeric).put("requestedAmount", 10000);
        assertThatThrownBy(() -> DigitalAssetRuntimeInput.from(numeric, scope()))
            .hasMessage("DIGITAL_ASSET_AMOUNT_INVALID");

        Map<String, Object> fractionalNumber = validInput();
        outbound(fractionalNumber).put("requestedAmount", 1.5);
        assertThatThrownBy(() -> DigitalAssetRuntimeInput.from(fractionalNumber, scope()))
            .hasMessage("DIGITAL_ASSET_AMOUNT_INVALID");

        Map<String, Object> fractionalString = validInput();
        outbound(fractionalString).put("requestedAmount", "1.5");
        assertThatThrownBy(() -> DigitalAssetRuntimeInput.from(fractionalString, scope()))
            .hasMessage("DIGITAL_ASSET_AMOUNT_INVALID");
    }

    @Test
    void appliesTheSameCallerInvariantsDuringShapeValidationAndParsing() {
        Map<String, Object> zero = validInput();
        outbound(zero).put("requestedAmount", "0");
        assertThatThrownBy(() -> DigitalAssetRuntimeInput.validateShape(zero))
            .hasMessage("DIGITAL_ASSET_OUTBOUND_REQUEST_INVALID");
        assertThatThrownBy(() -> DigitalAssetRuntimeInput.from(zero, scope()))
            .hasMessage("DIGITAL_ASSET_OUTBOUND_REQUEST_INVALID");

        Map<String, Object> regulatory = validInput();
        outbound(regulatory).put("regulatoryOutboundData", Map.of("travelRule", "value"));
        assertThatThrownBy(() -> DigitalAssetRuntimeInput.validateShape(regulatory))
            .hasMessage("DIGITAL_ASSET_REGULATORY_DATA_NOT_SUPPORTED");
        assertThatThrownBy(() -> DigitalAssetRuntimeInput.from(regulatory, scope()))
            .hasMessage("DIGITAL_ASSET_REGULATORY_DATA_NOT_SUPPORTED");
    }

    @Test
    void rejectsMissingAndUnknownOutboundFields() {
        Map<String, Object> missing = validInput();
        outbound(missing).remove("requestedAmount");
        assertThatThrownBy(() -> DigitalAssetRuntimeInput.from(missing, scope()))
            .hasMessage("DIGITAL_ASSET_OUTBOUND_REQUEST_SCHEMA_MISMATCH");

        Map<String, Object> unknown = validInput();
        outbound(unknown).put("approvedAmount", "10000");
        assertThatThrownBy(() -> DigitalAssetRuntimeInput.from(unknown, scope()))
            .hasMessage("DIGITAL_ASSET_OUTBOUND_REQUEST_SCHEMA_MISMATCH");
    }

    @Test
    void rejectsCallerReportedServerOwnedFields() {
        Map<String, Object> input = validInput();
        outbound(input).put("requestedAt", "2026-09-08T00:00:00Z");

        assertThatThrownBy(() -> DigitalAssetRuntimeInput.from(input, scope()))
            .hasMessage("DIGITAL_ASSET_OUTBOUND_REQUEST_SCHEMA_MISMATCH");
    }

    private Map<String, Object> validInput() {
        Map<String, Object> asset = new HashMap<>();
        asset.put("chainId", "eip155:1");
        asset.put("assetKind", "FUNGIBLE_TOKEN");
        asset.put("assetSymbol", "ASSET");
        asset.put("assetContractAddress", "0x0000000000000000000000000000000000000001");
        asset.put("operation", "TRANSFER");
        Map<String, Object> outbound = new HashMap<>();
        outbound.put("requestedAsset", asset);
        outbound.put("requestedAmount", "10000");
        outbound.put("requestedDestination", "wallet-001");
        outbound.put("requestedBeneficiaryReference", "beneficiary-001");
        outbound.put("regulatoryOutboundData", new HashMap<>());
        Map<String, Object> input = new HashMap<>();
        input.put("approvedTransactionReference", "approved-tx-001");
        input.put("customerId", "customer-001");
        input.put("accountId", "account-001");
        input.put("outboundRequest", outbound);
        return input;
    }

    private ExecutionPackRequestScope scope() {
        return new ExecutionPackRequestScope(
            "institution-a", "workload-a", "purpose-a", "subject-digest-a",
            "destination-a", "idem-a", OffsetDateTime.parse("2026-09-08T00:00:00Z")
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> outbound(Map<String, Object> input) {
        return (Map<String, Object>) input.get("outboundRequest");
    }
}
