package com.adp.gateway.digitalasset.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record ApprovedTransactionSnapshot(
    String approvedTransactionId,
    String version,
    String digest,
    String institutionId,
    String subjectRefDigest,
    String workloadId,
    String purpose,
    String approvedAssetId,
    BigDecimal approvedMaxAmount,
    String approvedDestinationProfileId,
    String approvedDestination,
    String approvedBeneficiaryReference,
    OffsetDateTime approvedFrom,
    OffsetDateTime approvedUntil
) {
    public ApprovedTransactionSnapshot {
        requireText(approvedTransactionId, "approvedTransactionId");
        requireText(version, "version");
        if (digest == null || !digest.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("digest must be a lowercase SHA-256 hex value");
        }
        requireText(institutionId, "institutionId");
        requireText(subjectRefDigest, "subjectRefDigest");
        requireText(workloadId, "workloadId");
        requireText(purpose, "purpose");
        requireText(approvedAssetId, "approvedAssetId");
        requireText(approvedDestinationProfileId, "approvedDestinationProfileId");
        requireText(approvedDestination, "approvedDestination");
        requireText(approvedBeneficiaryReference, "approvedBeneficiaryReference");
        if (approvedMaxAmount == null || approvedMaxAmount.signum() <= 0) {
            throw new IllegalArgumentException("approvedMaxAmount must be positive");
        }
        if (approvedFrom == null || approvedUntil == null || approvedFrom.isAfter(approvedUntil)) {
            throw new IllegalArgumentException("approved period is invalid");
        }
    }

    public boolean isEffectiveAt(OffsetDateTime instant) {
        return instant != null && !instant.isBefore(approvedFrom) && !instant.isAfter(approvedUntil);
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
