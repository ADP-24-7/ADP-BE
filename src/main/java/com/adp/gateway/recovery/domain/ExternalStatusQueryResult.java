package com.adp.gateway.recovery.domain;

import com.adp.gateway.connector.domain.ConnectorStatus;

public record ExternalStatusQueryResult(
    ConnectorStatus status,
    String evidenceDigest
) {
    public ExternalStatusQueryResult {
        if (status == null) {
            throw new IllegalArgumentException("External status is required");
        }
        if (evidenceDigest == null || !evidenceDigest.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Status query evidence digest must be SHA-256");
        }
    }
}
