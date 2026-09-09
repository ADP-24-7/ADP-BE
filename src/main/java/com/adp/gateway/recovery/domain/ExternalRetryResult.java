package com.adp.gateway.recovery.domain;

import com.adp.gateway.connector.domain.ConnectorStatus;

public record ExternalRetryResult(ConnectorStatus status, String evidenceDigest) {
    public ExternalRetryResult {
        if (status == null) {
            throw new IllegalArgumentException("External retry status is required");
        }
        if (evidenceDigest == null || !evidenceDigest.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("External retry evidence digest must be SHA-256");
        }
    }
}
