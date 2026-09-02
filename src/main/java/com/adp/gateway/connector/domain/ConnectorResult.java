package com.adp.gateway.connector.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;

public record ConnectorResult(
    String connectorExecutionId,
    String connectorId,
    ConnectorStatus status,
    String outboundPayloadId,
    String outboundCandidateDigest,
    String responseDigest,
    String responseSchemaVersion,
    @JsonIgnore Object responsePayload
) {

    public ConnectorResult(
        String connectorExecutionId,
        String connectorId,
        ConnectorStatus status,
        String outboundPayloadId,
        String outboundCandidateDigest,
        String responseDigest,
        String responseSchemaVersion
    ) {
        this(
            connectorExecutionId,
            connectorId,
            status,
            outboundPayloadId,
            outboundCandidateDigest,
            responseDigest,
            responseSchemaVersion,
            null
        );
    }

    public ConnectorResult(String connectorId, ConnectorStatus status) {
        this(null, connectorId, status, null, null, null, null, null);
    }

    public static ConnectorResult notExecuted(String connectorId) {
        return new ConnectorResult(null, connectorId, ConnectorStatus.NOT_SENT, null, null, null, null, null);
    }

    @Override
    public String toString() {
        return "ConnectorResult[connectorExecutionId=%s, connectorId=%s, status=%s, outboundPayloadId=%s, outboundCandidateDigest=%s, responseDigest=%s, responseSchemaVersion=%s, responsePayload=<redacted>]"
            .formatted(
                connectorExecutionId,
                connectorId,
                status,
                outboundPayloadId,
                outboundCandidateDigest,
                responseDigest,
                responseSchemaVersion
            );
    }
}
