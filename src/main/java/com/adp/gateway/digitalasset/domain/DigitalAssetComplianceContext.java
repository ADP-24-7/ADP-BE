package com.adp.gateway.digitalasset.domain;

public record DigitalAssetComplianceContext(
    String kycStatus,
    String amlStatus,
    boolean walletVerified,
    String sourceSystem,
    String assertionVersion,
    String evidenceDigest
) {
    public DigitalAssetComplianceContext {
        if (isBlank(kycStatus) || isBlank(amlStatus) || isBlank(sourceSystem) || isBlank(assertionVersion)) {
            throw new IllegalArgumentException("Compliance assertion values must not be blank");
        }
        if (evidenceDigest == null || !evidenceDigest.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("evidenceDigest must be SHA-256");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
