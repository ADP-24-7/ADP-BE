package com.adp.gateway.digitalasset.domain;

import java.util.Map;
import java.util.Set;

public record DigitalAssetDescriptor(
    String chainId,
    DigitalAssetKind assetKind,
    String assetSymbol,
    String assetContractAddress,
    DigitalAssetOperation operation,
    String tokenId
) {
    private static final Set<String> REQUIRED_KEYS = Set.of(
        "chainId", "assetKind", "assetSymbol", "operation"
    );
    private static final Set<String> ALLOWED_KEYS = Set.of(
        "chainId", "assetKind", "assetSymbol", "assetContractAddress", "operation", "tokenId"
    );

    public DigitalAssetDescriptor {
        requireText(chainId, "chainId", 80);
        requireText(assetSymbol, "assetSymbol", 64);
        if (assetKind == null || operation == null) {
            throw new IllegalArgumentException("DIGITAL_ASSET_ASSET_INVALID");
        }
        assetContractAddress = optionalText(assetContractAddress, "assetContractAddress", 240);
        tokenId = optionalText(tokenId, "tokenId", 160);
        if (assetKind == DigitalAssetKind.NATIVE && (assetContractAddress != null || tokenId != null)) {
            throw new IllegalArgumentException("DIGITAL_ASSET_ASSET_INVALID");
        }
        if (assetKind == DigitalAssetKind.FUNGIBLE_TOKEN
            && (assetContractAddress == null || tokenId != null)) {
            throw new IllegalArgumentException("DIGITAL_ASSET_ASSET_INVALID");
        }
        if (assetKind == DigitalAssetKind.NON_FUNGIBLE_TOKEN
            && (assetContractAddress == null || tokenId == null)) {
            throw new IllegalArgumentException("DIGITAL_ASSET_ASSET_INVALID");
        }
    }

    public static DigitalAssetDescriptor from(Object value) {
        if (!(value instanceof Map<?, ?> source)) {
            throw new IllegalArgumentException("DIGITAL_ASSET_ASSET_SCHEMA_MISMATCH");
        }
        Set<String> keys = stringKeys(source);
        if (!keys.containsAll(REQUIRED_KEYS) || !ALLOWED_KEYS.containsAll(keys)) {
            throw new IllegalArgumentException("DIGITAL_ASSET_ASSET_SCHEMA_MISMATCH");
        }
        return new DigitalAssetDescriptor(
            text(source.get("chainId")), enumValue(DigitalAssetKind.class, source.get("assetKind")),
            text(source.get("assetSymbol")), nullableText(source.get("assetContractAddress")),
            enumValue(DigitalAssetOperation.class, source.get("operation")), nullableText(source.get("tokenId"))
        );
    }

    public String canonicalValue() {
        return String.join("|", chainId, assetKind.name(), assetSymbol, value(assetContractAddress),
            operation.name(), value(tokenId));
    }

    private static Set<String> stringKeys(Map<?, ?> source) {
        return source.keySet().stream().map(String::valueOf).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static String text(Object value) {
        return value instanceof String text ? text : null;
    }

    private static String nullableText(Object value) {
        return value == null ? null : text(value);
    }

    private static <T extends Enum<T>> T enumValue(Class<T> type, Object value) {
        if (!(value instanceof String text)) {
            throw new IllegalArgumentException("DIGITAL_ASSET_ASSET_INVALID");
        }
        try {
            return Enum.valueOf(type, text);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("DIGITAL_ASSET_ASSET_INVALID", exception);
        }
    }

    private static void requireText(String value, String name, int maxLength) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw new IllegalArgumentException(name + " is invalid");
        }
    }

    private static String optionalText(String value, String name, int maxLength) {
        if (value == null) {
            return null;
        }
        requireText(value, name, maxLength);
        return value;
    }

    private static String value(String value) {
        return value == null ? "<none>" : value;
    }
}
