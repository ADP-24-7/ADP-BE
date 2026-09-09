package com.adp.gateway.observability.domain;

public record OperationalMetricSnapshot(
    long recoveryBacklog,
    long recoveryOldestAgeSeconds,
    long recoveryManualReview,
    long recoveryExhausted,
    long policyCurrentSelections,
    long policyDriftedSelections
) {
    public static OperationalMetricSnapshot empty() {
        return new OperationalMetricSnapshot(0, 0, 0, 0, 0, 0);
    }
}
