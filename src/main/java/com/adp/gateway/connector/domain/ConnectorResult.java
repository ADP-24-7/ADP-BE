package com.adp.gateway.connector.domain;

public record ConnectorResult(
    String connectorExecutionId,
    String connectorId,
    String status,
    String outboundPayloadId,
    String outboundPayloadDigest,
    String responseDigest,
    String responseSchemaVersion,
    boolean responseLeakageDetected
) {

    public ConnectorResult(String connectorId, String status) {
        this(null, connectorId, status, null, null, null, null, false);
    }

    public static ConnectorResult notExecuted(String connectorId) {
        return new ConnectorResult(null, connectorId, "NOT_EXECUTED", null, null, null, null, false);
    }
}
