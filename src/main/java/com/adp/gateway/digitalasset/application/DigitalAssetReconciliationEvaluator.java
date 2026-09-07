package com.adp.gateway.digitalasset.application;

import java.util.Map;
import java.util.TreeMap;

import com.adp.gateway.context.application.CanonicalValueHasher;
import com.adp.gateway.digitalasset.domain.DigitalAssetReconciliationAssessment;
import com.adp.gateway.digitalasset.domain.DigitalAssetMismatchField;
import com.adp.gateway.digitalasset.domain.DigitalAssetReconciliationResult;
import org.springframework.stereotype.Component;

@Component
public class DigitalAssetReconciliationEvaluator {
    private static final java.util.Set<String> TRANSACTION_FIELDS = java.util.Arrays.stream(
            DigitalAssetMismatchField.values()
        )
        .filter(field -> field.externalName() != null && field != DigitalAssetMismatchField.EXTERNAL_REQUEST_ID)
        .map(DigitalAssetMismatchField::externalName)
        .collect(java.util.stream.Collectors.toUnmodifiableSet());
    private final CanonicalValueHasher hasher;

    public DigitalAssetReconciliationEvaluator(CanonicalValueHasher hasher) {
        this.hasher = hasher;
    }

    public DigitalAssetReconciliationAssessment evaluate(
        Map<String, Object> requestPayload,
        Map<?, ?> response,
        String settlementStatus
    ) {
        Map<String, Object> expected = map(requestPayload.get("transaction"));
        Map<String, Object> actual = map(response.get("settledTransaction"));
        String expectedDigest = digest(expected);
        String actualDigest = digest(actual);
        if (!"SETTLED".equals(settlementStatus)) {
            return new DigitalAssetReconciliationAssessment(
                DigitalAssetReconciliationResult.WAIT, java.util.List.of(), expectedDigest, actualDigest
            );
        }
        var mismatchedFields = java.util.Arrays.stream(DigitalAssetMismatchField.values())
            .filter(field -> field != DigitalAssetMismatchField.EXTERNAL_REQUEST_ID
                && field != DigitalAssetMismatchField.UNEXPECTED_FIELD)
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
        DigitalAssetReconciliationResult result = mismatchedFields.isEmpty()
            ? DigitalAssetReconciliationResult.MATCH
            : mismatchedFields.stream().anyMatch(DigitalAssetMismatchField::critical)
                ? DigitalAssetReconciliationResult.CRITICAL_MISMATCH
                : DigitalAssetReconciliationResult.MISMATCH;
        return new DigitalAssetReconciliationAssessment(result, mismatchedFields, expectedDigest, actualDigest);
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

    private String digest(Map<String, Object> value) {
        return hasher.hash(value.entrySet().stream()
            .map(entry -> entry.getKey() + "=" + String.valueOf(entry.getValue()))
            .collect(java.util.stream.Collectors.joining("|")));
    }
}
