package com.adp.gateway.connector.domain;

public record ConnectorExecutionEvidence(
    String measurementType,
    Long timeToFirstResponseMillis,
    Long providerLatencyMillis,
    Integer inputTokens,
    Integer outputTokens,
    Integer totalTokens,
    String errorCategory
) {
    public ConnectorExecutionEvidence {
        requireNonNegative(timeToFirstResponseMillis, "timeToFirstResponseMillis");
        requireNonNegative(providerLatencyMillis, "providerLatencyMillis");
        requireNonNegative(inputTokens, "inputTokens");
        requireNonNegative(outputTokens, "outputTokens");
        requireNonNegative(totalTokens, "totalTokens");
        if (inputTokens != null && outputTokens != null && totalTokens != null
            && inputTokens + outputTokens != totalTokens) {
            throw new IllegalArgumentException("totalTokens must equal inputTokens + outputTokens");
        }
    }

    private static void requireNonNegative(Number value, String field) {
        if (value != null && value.longValue() < 0) {
            throw new IllegalArgumentException(field + " must not be negative");
        }
    }

    public static ConnectorExecutionEvidence mock() {
        return new ConnectorExecutionEvidence("MOCK", 0L, 0L, null, null, null, "NONE");
    }
}
