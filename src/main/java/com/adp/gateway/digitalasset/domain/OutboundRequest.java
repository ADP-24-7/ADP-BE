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
        validateCallerControlled(
            requestedAsset, requestedAmount, requestedDestination,
            requestedBeneficiaryReference, regulatoryOutboundData
        );
        if (requestedAt == null) {
            throw new IllegalArgumentException("DIGITAL_ASSET_OUTBOUND_REQUEST_INVALID");
        }
        requireText(destinationProfileId, "destinationProfileId", 120);
        requireText(idempotencyKey, "idempotencyKey", 120);
        regulatoryOutboundData = Map.copyOf(regulatoryOutboundData);
    }

    public String canonicalBindingValue() {
        return requestedAsset.canonicalValue() + "|" + requestedDestination + "|" + requestedAmount;
    }

    static void validateCallerControlled(
        DigitalAssetDescriptor requestedAsset,
        DigitalAssetAmount requestedAmount,
        String requestedDestination,
        String requestedBeneficiaryReference,
        Map<String, String> regulatoryOutboundData
    ) {
        if (requestedAsset == null || requestedAmount == null || requestedAmount.atomicUnits().signum() <= 0) {
            throw new IllegalArgumentException("DIGITAL_ASSET_OUTBOUND_REQUEST_INVALID");
        }
        requireText(requestedDestination, "requestedDestination", 240);
        requireText(requestedBeneficiaryReference, "requestedBeneficiaryReference", 240);
        if (regulatoryOutboundData == null || !regulatoryOutboundData.isEmpty()) {
            throw new IllegalArgumentException("DIGITAL_ASSET_REGULATORY_DATA_NOT_SUPPORTED");
        }
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
