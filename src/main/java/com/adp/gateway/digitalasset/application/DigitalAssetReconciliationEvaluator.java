package com.adp.gateway.digitalasset.application;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import com.adp.gateway.context.application.CanonicalValueHasher;
import com.adp.gateway.digitalasset.domain.DigitalAssetReconciliationAssessment;
import org.springframework.stereotype.Component;

@Component
public class DigitalAssetReconciliationEvaluator {
    private static final Set<String> CRITICAL_FIELDS = Set.of("walletAddress", "assetId", "amount");
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
            return new DigitalAssetReconciliationAssessment("WAIT", java.util.List.of(), expectedDigest, actualDigest);
        }
        var mismatchedFields = java.util.stream.Stream.concat(expected.keySet().stream(), actual.keySet().stream())
            .distinct()
            .filter(field -> !String.valueOf(expected.get(field)).equals(String.valueOf(actual.get(field))))
            .sorted()
            .toList();
        String result = mismatchedFields.isEmpty()
            ? "MATCH"
            : mismatchedFields.stream().anyMatch(CRITICAL_FIELDS::contains) ? "CRITICAL_MISMATCH" : "MISMATCH";
        return new DigitalAssetReconciliationAssessment(result, mismatchedFields, expectedDigest, actualDigest);
    }

    public DigitalAssetReconciliationAssessment criticalCorrelationMismatch(String expected, String actual) {
        return new DigitalAssetReconciliationAssessment(
            "CRITICAL_MISMATCH",
            java.util.List.of("externalRequestId"),
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
