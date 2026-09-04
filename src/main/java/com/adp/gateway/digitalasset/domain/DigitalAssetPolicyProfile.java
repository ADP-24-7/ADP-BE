package com.adp.gateway.digitalasset.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Set;

public record DigitalAssetPolicyProfile(
    String profileId,
    String version,
    String digest,
    String destinationProfileId,
    Set<String> allowedKycStatuses,
    Set<String> allowedAmlStatuses,
    boolean walletVerificationRequired,
    BigDecimal amountLimit,
    OffsetDateTime effectiveAt,
    OffsetDateTime expiresAt
) {
    public DigitalAssetPolicyProfile {
        allowedKycStatuses = Set.copyOf(allowedKycStatuses);
        allowedAmlStatuses = Set.copyOf(allowedAmlStatuses);
    }

    public boolean isEffectiveAt(OffsetDateTime at) {
        return !effectiveAt.isAfter(at) && (expiresAt == null || expiresAt.isAfter(at));
    }
}
