package com.adp.gateway.digitalasset.domain;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Set;

public record ExternalExecutionResult(
    String externalRequestId,
    String externalReference,
    String transactionHash,
    DigitalAssetExternalStatus externalStatus,
    DigitalAssetProviderStatus providerStatus,
    DigitalAssetReceiptStatus receiptStatus,
    DigitalAssetFinalityStatus finalityStatus,
    String executedChainId,
    String executedRecipientAddress,
    DigitalAssetKind executedAssetKind,
    String executedAssetSymbol,
    String executedAssetContractAddress,
    DigitalAssetAmount executedAmount,
    DigitalAssetAmount nativeValue,
    DigitalAssetOperation operation,
    String tokenId,
    String tokenTransferEvidenceRef,
    String internalTraceEvidenceRef,
    OffsetDateTime executedAt,
    OffsetDateTime finalizedAt,
    String responseDigest
) {
    private static final Set<String> KEYS = Set.of(
        "externalRequestId", "externalReference", "transactionHash", "externalStatus", "providerStatus",
        "receiptStatus", "finalityStatus", "executedChainId", "executedRecipientAddress",
        "executedAssetKind", "executedAssetSymbol", "executedAssetContractAddress", "executedAmount",
        "nativeValue", "operation", "tokenId", "tokenTransferEvidenceRef", "internalTraceEvidenceRef",
        "executedAt", "finalizedAt"
    );

    public ExternalExecutionResult {
        requireText(externalRequestId, "externalRequestId");
        requireText(externalReference, "externalReference");
        requireText(responseDigest, "responseDigest");
        if (externalStatus == null || providerStatus == null || receiptStatus == null || finalityStatus == null) {
            throw new IllegalArgumentException("DIGITAL_ASSET_EXTERNAL_STATUS_INVALID");
        }
        transactionHash = optional(transactionHash, "transactionHash");
        if (externalStatus == DigitalAssetExternalStatus.SETTLED) {
            requireFinalizedExecution(
                transactionHash, providerStatus, receiptStatus, finalityStatus, executedChainId,
                executedRecipientAddress, executedAssetKind, executedAssetSymbol, executedAssetContractAddress,
                executedAmount, operation, tokenId, executedAt, finalizedAt
            );
        }
    }

    public static ExternalExecutionResult from(Map<?, ?> source, String responseDigest) {
        if (source == null || !stringKeys(source).equals(KEYS)) {
            throw new IllegalArgumentException("DIGITAL_ASSET_EXTERNAL_RESULT_SCHEMA_MISMATCH");
        }
        return new ExternalExecutionResult(
            text(source.get("externalRequestId")), text(source.get("externalReference")),
            nullableText(source.get("transactionHash")), enumValue(DigitalAssetExternalStatus.class, source.get("externalStatus")),
            enumValue(DigitalAssetProviderStatus.class, source.get("providerStatus")),
            enumValue(DigitalAssetReceiptStatus.class, source.get("receiptStatus")),
            enumValue(DigitalAssetFinalityStatus.class, source.get("finalityStatus")),
            nullableText(source.get("executedChainId")), nullableText(source.get("executedRecipientAddress")),
            nullableEnum(DigitalAssetKind.class, source.get("executedAssetKind")), nullableText(source.get("executedAssetSymbol")),
            nullableText(source.get("executedAssetContractAddress")), nullableAmount(source.get("executedAmount")),
            nullableAmount(source.get("nativeValue")), nullableEnum(DigitalAssetOperation.class, source.get("operation")),
            nullableText(source.get("tokenId")), nullableText(source.get("tokenTransferEvidenceRef")),
            nullableText(source.get("internalTraceEvidenceRef")), nullableTime(source.get("executedAt")),
            nullableTime(source.get("finalizedAt")), responseDigest
        );
    }

    public boolean isFinalSuccess() {
        return externalStatus == DigitalAssetExternalStatus.SETTLED
            && providerStatus != DigitalAssetProviderStatus.FAILED
            && receiptStatus == DigitalAssetReceiptStatus.SUCCESS
            && finalityStatus == DigitalAssetFinalityStatus.FINALIZED;
    }

    public Map<String, Object> executionProjection() {
        Map<String, Object> result = new java.util.TreeMap<>();
        put(result, "chainId", executedChainId);
        put(result, "recipientAddress", executedRecipientAddress);
        put(result, "assetKind", executedAssetKind == null ? null : executedAssetKind.name());
        put(result, "assetSymbol", executedAssetSymbol);
        put(result, "assetContractAddress", executedAssetContractAddress);
        put(result, "amount", executedAmount == null ? null : executedAmount.toString());
        put(result, "operation", operation == null ? null : operation.name());
        put(result, "tokenId", tokenId);
        return Map.copyOf(result);
    }

    private static void requireFinalizedExecution(
        String transactionHash,
        DigitalAssetProviderStatus providerStatus,
        DigitalAssetReceiptStatus receiptStatus,
        DigitalAssetFinalityStatus finalityStatus,
        String chainId,
        String recipient,
        DigitalAssetKind kind,
        String symbol,
        String contractAddress,
        DigitalAssetAmount amount,
        DigitalAssetOperation operation,
        String tokenId,
        OffsetDateTime executedAt,
        OffsetDateTime finalizedAt
    ) {
        if (transactionHash == null || providerStatus == DigitalAssetProviderStatus.FAILED
            || receiptStatus != DigitalAssetReceiptStatus.SUCCESS
            || finalityStatus != DigitalAssetFinalityStatus.FINALIZED || chainId == null || recipient == null
            || kind == null || symbol == null || amount == null || operation == null
            || executedAt == null || finalizedAt == null || finalizedAt.isBefore(executedAt)) {
            throw new IllegalArgumentException("DIGITAL_ASSET_FINAL_EXECUTION_EVIDENCE_INCOMPLETE");
        }
        new DigitalAssetDescriptor(chainId, kind, symbol, contractAddress, operation, tokenId);
    }

    private static Set<String> stringKeys(Map<?, ?> source) {
        return source.keySet().stream().map(String::valueOf).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static <T extends Enum<T>> T enumValue(Class<T> type, Object value) {
        if (!(value instanceof String text)) {
            throw new IllegalArgumentException("DIGITAL_ASSET_EXTERNAL_RESULT_INVALID");
        }
        try {
            return Enum.valueOf(type, text);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("DIGITAL_ASSET_EXTERNAL_RESULT_INVALID", exception);
        }
    }

    private static <T extends Enum<T>> T nullableEnum(Class<T> type, Object value) {
        return value == null ? null : enumValue(type, value);
    }

    private static DigitalAssetAmount nullableAmount(Object value) {
        return value == null ? null : DigitalAssetAmount.from(value);
    }

    private static OffsetDateTime nullableTime(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return OffsetDateTime.parse(text(value));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("DIGITAL_ASSET_EXTERNAL_RESULT_INVALID", exception);
        }
    }

    private static String text(Object value) {
        if (!(value instanceof String text) || text.isBlank() || text.length() > 240) {
            throw new IllegalArgumentException("DIGITAL_ASSET_EXTERNAL_RESULT_INVALID");
        }
        return text;
    }

    private static String nullableText(Object value) {
        return value == null ? null : text(value);
    }

    private static String optional(String value, String name) {
        if (value != null) {
            requireText(value, name);
        }
        return value;
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank() || value.length() > 240) {
            throw new IllegalArgumentException(name + " is invalid");
        }
    }

    private static void put(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }
}
