package com.adp.gateway.connector.domain;

public record ConnectorResult(
    String connectorId,
    String status,
    String outboundPayloadId,
    String outboundPayloadDigest
) {

    public ConnectorResult(String connectorId, String status) {
        this(connectorId, status, null, null);
    }

    public static ConnectorResult notExecuted(String connectorId) {
        return new ConnectorResult(connectorId, "NOT_EXECUTED", null, null);
    }
}
