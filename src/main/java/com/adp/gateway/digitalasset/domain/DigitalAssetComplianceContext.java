package com.adp.gateway.digitalasset.domain;

import java.util.Objects;

public record DigitalAssetComplianceContext(
    String kycStatus,
    String amlStatus,
    boolean walletVerified,
    String sourceSystem,
    String assertionVersion,
    String evidenceDigest
) {
    public DigitalAssetComplianceContext {
        Objects.requireNonNull(kycStatus, "kycStatus must not be null");
        Objects.requireNonNull(amlStatus, "amlStatus must not be null");
        Objects.requireNonNull(sourceSystem, "sourceSystem must not be null");
        Objects.requireNonNull(assertionVersion, "assertionVersion must not be null");
        if (evidenceDigest == null || !evidenceDigest.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("evidenceDigest must be SHA-256");
        }
    }
}
