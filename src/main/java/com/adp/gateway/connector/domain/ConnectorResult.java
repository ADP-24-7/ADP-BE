package com.adp.gateway.connector.domain;

public record ConnectorResult(
    String connectorExecutionId,
    String connectorId,
    ConnectorStatus status,
    String outboundPayloadId,
    String outboundCandidateDigest,
    String responseDigest,
    String responseSchemaVersion
) {

    public ConnectorResult(String connectorId, ConnectorStatus status) {
        this(null, connectorId, status, null, null, null, null);
    }

    public static ConnectorResult notExecuted(String connectorId) {
        return new ConnectorResult(null, connectorId, ConnectorStatus.NOT_SENT, null, null, null, null);
    }
}
