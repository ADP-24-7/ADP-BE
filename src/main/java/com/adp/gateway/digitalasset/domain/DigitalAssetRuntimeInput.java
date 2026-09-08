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
        if (scope == null) {
            throw new IllegalArgumentException("DIGITAL_ASSET_RUNTIME_SCOPE_MISMATCH");
        }
        CallerControlledInput parsed = parseCallerControlled(input);
        return new DigitalAssetRuntimeInput(
            parsed.approvedTransactionReference(), parsed.customerId(), parsed.accountId(),
            new OutboundRequest(
                parsed.requestedAsset(), parsed.requestedAmount(), parsed.requestedDestination(),
                parsed.requestedBeneficiaryReference(), scope.requestStartedAt(), parsed.regulatoryOutboundData(),
                scope.destinationProfileId(),
                scope.idempotencyKey()
            )
        );
    }

    public static void validateShape(Map<String, Object> input) {
        parseCallerControlled(input);
    }

    private static CallerControlledInput parseCallerControlled(Map<String, Object> input) {
        if (input == null || !input.keySet().equals(KEYS)) {
            throw new IllegalArgumentException("DIGITAL_ASSET_INPUT_SCHEMA_MISMATCH");
        }
        Map<?, ?> outbound = map(input.get("outboundRequest"));
        if (!stringKeys(outbound).equals(OUTBOUND_KEYS)) {
            throw new IllegalArgumentException("DIGITAL_ASSET_OUTBOUND_REQUEST_SCHEMA_MISMATCH");
        }
        ApprovedTransactionReference reference =
            new ApprovedTransactionReference(text(input.get("approvedTransactionReference")));
        String customerId = text(input.get("customerId"));
        String accountId = text(input.get("accountId"));
        DigitalAssetDescriptor asset = DigitalAssetDescriptor.from(outbound.get("requestedAsset"));
        DigitalAssetAmount amount = DigitalAssetAmount.fromWire(outbound.get("requestedAmount"));
        String destination = text(outbound.get("requestedDestination"));
        String beneficiary = text(outbound.get("requestedBeneficiaryReference"));
        Map<String, String> regulatoryData = regulatoryData(outbound.get("regulatoryOutboundData"));
        OutboundRequest.validateCallerControlled(asset, amount, destination, beneficiary, regulatoryData);
        return new CallerControlledInput(
            reference, customerId, accountId, asset, amount, destination, beneficiary, regulatoryData
        );
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

    private record CallerControlledInput(
        ApprovedTransactionReference approvedTransactionReference,
        String customerId,
        String accountId,
        DigitalAssetDescriptor requestedAsset,
        DigitalAssetAmount requestedAmount,
        String requestedDestination,
        String requestedBeneficiaryReference,
        Map<String, String> regulatoryOutboundData
    ) {
    }
}
