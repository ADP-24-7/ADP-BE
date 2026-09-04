package com.adp.gateway.digitalasset.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.Objects;

public record DigitalAssetPolicyProfile(
    String profileId,
    String version,
    String digest,
    String destinationProfileId,
    String complianceSourceSystem,
    String complianceAssertionVersion,
    Set<String> allowedKycStatuses,
    Set<String> allowedAmlStatuses,
    boolean walletVerificationRequired,
    BigDecimal amountLimit,
    OffsetDateTime effectiveAt,
    OffsetDateTime expiresAt
) {
    public DigitalAssetPolicyProfile {
        Objects.requireNonNull(effectiveAt, "effectiveAt must not be null");
        allowedKycStatuses = Set.copyOf(allowedKycStatuses);
        allowedAmlStatuses = Set.copyOf(allowedAmlStatuses);
        if (profileId == null || profileId.isBlank() || version == null || version.isBlank()) {
            throw new IllegalArgumentException("Policy profile identity is required");
        }
        if (digest == null || !digest.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Policy profile digest must be SHA-256");
        }
        if (destinationProfileId == null || destinationProfileId.isBlank()
            || complianceSourceSystem == null || complianceSourceSystem.isBlank()
            || complianceAssertionVersion == null || complianceAssertionVersion.isBlank()
            || allowedKycStatuses.isEmpty() || allowedAmlStatuses.isEmpty()) {
            throw new IllegalArgumentException("Policy profile scope and statuses are required");
        }
        if (amountLimit == null || amountLimit.signum() <= 0) {
            throw new IllegalArgumentException("Policy profile amount limit must be positive");
        }
        if (expiresAt != null && !expiresAt.isAfter(effectiveAt)) {
            throw new IllegalArgumentException("Policy profile expiry must be after effectiveAt");
        }
    }

    public boolean isEffectiveAt(OffsetDateTime at) {
        return !effectiveAt.isAfter(at) && (expiresAt == null || expiresAt.isAfter(at));
    }
}
