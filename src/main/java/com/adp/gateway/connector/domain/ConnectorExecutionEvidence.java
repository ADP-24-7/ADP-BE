package com.adp.gateway.connector.domain;

public record ConnectorExecutionEvidence(
    ConnectorMeasurementType measurementType,
    Long fullResponseLatencyMillis,
    Long attemptElapsedMillis,
    Integer inputTokens,
    Integer outputTokens,
    Integer totalTokens,
    TokenUsageStatus tokenUsageStatus,
    ConnectorErrorCategory errorCategory
) {
    public static ConnectorExecutionEvidence mock() {
        return new ConnectorExecutionEvidence(
            ConnectorMeasurementType.MOCK, 0L, null, null, null, null,
            TokenUsageStatus.NOT_PROVIDED, ConnectorErrorCategory.NONE
        );
    }
}
