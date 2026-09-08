package com.adp.gateway.digitalasset.domain;

import java.time.OffsetDateTime;
import java.util.Map;

public record OutboundRequest(
    DigitalAssetDescriptor requestedAsset,
    DigitalAssetAmount requestedAmount,
    String requestedDestination,
    String requestedBeneficiaryReference,
    OffsetDateTime requestedAt,
    Map<String, String> regulatoryOutboundData,
    String destinationProfileId,
    String idempotencyKey
) {
    public OutboundRequest {
        if (requestedAsset == null || requestedAmount == null || requestedAmount.atomicUnits().signum() <= 0
            || requestedAt == null) {
            throw new IllegalArgumentException("DIGITAL_ASSET_OUTBOUND_REQUEST_INVALID");
        }
        requireText(requestedDestination, "requestedDestination", 240);
        requireText(requestedBeneficiaryReference, "requestedBeneficiaryReference", 240);
        requireText(destinationProfileId, "destinationProfileId", 120);
        requireText(idempotencyKey, "idempotencyKey", 120);
        regulatoryOutboundData = Map.copyOf(regulatoryOutboundData == null ? Map.of() : regulatoryOutboundData);
        if (regulatoryOutboundData.size() > 20 || regulatoryOutboundData.entrySet().stream().anyMatch(entry ->
            invalid(entry.getKey(), 80) || invalid(entry.getValue(), 240))) {
            throw new IllegalArgumentException("DIGITAL_ASSET_REGULATORY_DATA_INVALID");
        }
    }

    public String canonicalBindingValue() {
        return requestedAsset.canonicalValue() + "|" + requestedDestination + "|" + requestedAmount;
    }

    private static void requireText(String value, String name, int maxLength) {
        if (invalid(value, maxLength)) {
            throw new IllegalArgumentException(name + " is invalid");
        }
    }

    private static boolean invalid(String value, int maxLength) {
        return value == null || value.isBlank() || value.length() > maxLength;
    }
}
