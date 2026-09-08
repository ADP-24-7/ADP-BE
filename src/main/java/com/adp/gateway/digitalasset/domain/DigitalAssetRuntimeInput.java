package com.adp.gateway.digitalasset.domain;

import java.util.Map;
import java.util.Set;

import com.adp.gateway.context.application.ExecutionPackRequestScope;

public record DigitalAssetRuntimeInput(
    ApprovedTransactionReference approvedTransactionReference,
    String customerId,
    String accountId,
    OutboundRequest outboundRequest
) {
    private static final Set<String> KEYS = Set.of(
        "approvedTransactionReference", "customerId", "accountId", "outboundRequest"
    );
    private static final Set<String> OUTBOUND_KEYS = Set.of(
        "requestedAsset", "requestedAmount", "requestedDestination",
        "requestedBeneficiaryReference", "regulatoryOutboundData"
    );

    public static DigitalAssetRuntimeInput from(Map<String, Object> input, ExecutionPackRequestScope scope) {
        if (scope == null || input == null || !input.keySet().equals(KEYS)) {
            throw new IllegalArgumentException("DIGITAL_ASSET_INPUT_SCHEMA_MISMATCH");
        }
        Map<?, ?> outbound = map(input.get("outboundRequest"));
        if (!stringKeys(outbound).equals(OUTBOUND_KEYS)) {
            throw new IllegalArgumentException("DIGITAL_ASSET_OUTBOUND_REQUEST_SCHEMA_MISMATCH");
        }
        return new DigitalAssetRuntimeInput(
            new ApprovedTransactionReference(text(input.get("approvedTransactionReference"))),
            text(input.get("customerId")),
            text(input.get("accountId")),
            new OutboundRequest(
                DigitalAssetDescriptor.from(outbound.get("requestedAsset")),
                DigitalAssetAmount.from(outbound.get("requestedAmount")),
                text(outbound.get("requestedDestination")),
                text(outbound.get("requestedBeneficiaryReference")),
                scope.requestStartedAt(),
                regulatoryData(outbound.get("regulatoryOutboundData")),
                scope.destinationProfileId(),
                scope.idempotencyKey()
            )
        );
    }

    public static void validateShape(Map<String, Object> input) {
        if (input == null || !input.keySet().equals(KEYS)) {
            throw new IllegalArgumentException("DIGITAL_ASSET_INPUT_SCHEMA_MISMATCH");
        }
        Map<?, ?> outbound = map(input.get("outboundRequest"));
        if (!stringKeys(outbound).equals(OUTBOUND_KEYS)) {
            throw new IllegalArgumentException("DIGITAL_ASSET_OUTBOUND_REQUEST_SCHEMA_MISMATCH");
        }
        new ApprovedTransactionReference(text(input.get("approvedTransactionReference")));
        text(input.get("customerId"));
        text(input.get("accountId"));
        DigitalAssetDescriptor.from(outbound.get("requestedAsset"));
        DigitalAssetAmount.from(outbound.get("requestedAmount"));
        text(outbound.get("requestedDestination"));
        text(outbound.get("requestedBeneficiaryReference"));
        regulatoryData(outbound.get("regulatoryOutboundData"));
    }

    private static Map<?, ?> map(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("DIGITAL_ASSET_OUTBOUND_REQUEST_SCHEMA_MISMATCH");
        }
        return map;
    }

    private static Map<String, String> regulatoryData(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("DIGITAL_ASSET_REGULATORY_DATA_INVALID");
        }
        Map<String, String> result = new java.util.TreeMap<>();
        map.forEach((key, item) -> {
            if (!(key instanceof String textKey) || !(item instanceof String textValue)) {
                throw new IllegalArgumentException("DIGITAL_ASSET_REGULATORY_DATA_INVALID");
            }
            result.put(textKey, textValue);
        });
        return Map.copyOf(result);
    }

    private static Set<String> stringKeys(Map<?, ?> source) {
        return source.keySet().stream().map(String::valueOf).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static String text(Object value) {
        if (!(value instanceof String text) || text.isBlank() || text.length() > 240) {
            throw new IllegalArgumentException("DIGITAL_ASSET_INPUT_INVALID");
        }
        return text;
    }
}
