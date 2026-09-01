package com.adp.gateway.connector.domain;

public record ConnectorResult(
    String connectorExecutionId,
    String connectorId,
    String status,
    String outboundPayloadId,
    String outboundCandidateDigest,
    String responseDigest,
    String responseSchemaVersion
) {

    public ConnectorResult(String connectorId, String status) {
        this(null, connectorId, status, null, null, null, null);
    }

    public static ConnectorResult notExecuted(String connectorId) {
        return new ConnectorResult(null, connectorId, "NOT_EXECUTED", null, null, null, null);
    }
}
