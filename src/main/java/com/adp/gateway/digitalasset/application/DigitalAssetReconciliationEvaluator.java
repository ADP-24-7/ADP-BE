package com.adp.gateway.digitalasset.application;

import java.util.Map;
import java.util.TreeMap;

import com.adp.gateway.context.application.CanonicalValueHasher;
import com.adp.gateway.digitalasset.domain.DigitalAssetReconciliationAssessment;
import com.adp.gateway.digitalasset.domain.DigitalAssetMismatchField;
import com.adp.gateway.digitalasset.domain.DigitalAssetReconciliationResult;
import com.adp.gateway.digitalasset.domain.ExternalExecutionResult;
import org.springframework.stereotype.Component;

@Component
public class DigitalAssetReconciliationEvaluator {
    private static final java.util.Set<DigitalAssetMismatchField> ACTIVE_TRANSACTION_FIELDS = java.util.Set.of(
        DigitalAssetMismatchField.CHAIN_ID,
        DigitalAssetMismatchField.RECIPIENT_ADDRESS,
        DigitalAssetMismatchField.ASSET_KIND,
        DigitalAssetMismatchField.ASSET_SYMBOL,
        DigitalAssetMismatchField.ASSET_CONTRACT_ADDRESS,
        DigitalAssetMismatchField.AMOUNT,
        DigitalAssetMismatchField.OPERATION,
        DigitalAssetMismatchField.TOKEN_ID
    );
    private static final java.util.Set<String> TRANSACTION_FIELDS = ACTIVE_TRANSACTION_FIELDS.stream()
        .map(DigitalAssetMismatchField::externalName)
        .collect(java.util.stream.Collectors.toUnmodifiableSet());
    private final CanonicalValueHasher hasher;

    public DigitalAssetReconciliationEvaluator(CanonicalValueHasher hasher) {
        this.hasher = hasher;
    }

    public DigitalAssetReconciliationAssessment evaluate(
        Map<String, Object> requestPayload,
        ExternalExecutionResult result
    ) {
        Map<String, Object> expected = projection(map(requestPayload.get("transaction")));
        Map<String, Object> actual = result.executionProjection();
        String expectedDigest = digest(expected);
        String actualDigest = digest(actual);
        if (!result.isFinalSuccess()) {
            return new DigitalAssetReconciliationAssessment(
                DigitalAssetReconciliationResult.WAIT, java.util.List.of(), expectedDigest, actualDigest
            );
        }
        var mismatchedFields = ACTIVE_TRANSACTION_FIELDS.stream()
            .filter(field -> !java.util.Objects.equals(
                expected.get(field.externalName()), actual.get(field.externalName())
            ))
            .toList();
        boolean unexpectedField = actual.keySet().stream().anyMatch(key -> !TRANSACTION_FIELDS.contains(key));
        if (unexpectedField) {
            mismatchedFields = java.util.stream.Stream.concat(
                mismatchedFields.stream(), java.util.stream.Stream.of(DigitalAssetMismatchField.UNEXPECTED_FIELD)
            ).toList();
            actualDigest = hasher.hash(actualDigest + "|UNEXPECTED_FIELD_PRESENT");
        }
        DigitalAssetReconciliationResult reconciliationResult = mismatchedFields.isEmpty()
            ? DigitalAssetReconciliationResult.MATCH
            : mismatchedFields.stream().anyMatch(DigitalAssetMismatchField::critical)
                ? DigitalAssetReconciliationResult.CRITICAL_MISMATCH
                : DigitalAssetReconciliationResult.MISMATCH;
        return new DigitalAssetReconciliationAssessment(
            reconciliationResult, mismatchedFields, expectedDigest, actualDigest
        );
    }

    public DigitalAssetReconciliationAssessment criticalCorrelationMismatch(String expected, String actual) {
        return new DigitalAssetReconciliationAssessment(
            DigitalAssetReconciliationResult.CRITICAL_MISMATCH,
            java.util.List.of(DigitalAssetMismatchField.EXTERNAL_REQUEST_ID),
            hasher.hash("externalRequestId=" + String.valueOf(expected)),
            hasher.hash("externalRequestId=" + String.valueOf(actual))
        );
    }

    private Map<String, Object> map(Object value) {
        Map<String, Object> result = new TreeMap<>();
        if (value instanceof Map<?, ?> map) {
            map.forEach((key, item) -> result.put(String.valueOf(key), item));
        }
        return result;
    }

    private Map<String, Object> projection(Map<String, Object> value) {
        Map<String, Object> result = new TreeMap<>();
        TRANSACTION_FIELDS.forEach(field -> {
            if (value.get(field) != null) {
                result.put(field, value.get(field));
            }
        });
        return result;
    }

    private String digest(Map<String, Object> value) {
        return hasher.hash(value.entrySet().stream()
            .map(entry -> entry.getKey() + "=" + String.valueOf(entry.getValue()))
            .collect(java.util.stream.Collectors.joining("|")));
    }
}
