package com.adp.gateway.digitalasset.domain;

import java.time.OffsetDateTime;

public record ApprovedTransaction(
    String approvedTransactionId,
    String version,
    String digest,
    String institutionId,
    String subjectRefDigest,
    String workloadId,
    String purpose,
    String approvedPolicySnapshotId,
    DigitalAssetDescriptor approvedAsset,
    DigitalAssetAmount approvedAmount,
    DigitalAssetAmount approvedAmountLimit,
    String approvedDestinationProfileId,
    String approvedDestination,
    String approvedBeneficiaryReference,
    OffsetDateTime approvedFrom,
    OffsetDateTime approvedUntil
) {
    public ApprovedTransaction {
        requireText(approvedTransactionId, "approvedTransactionId");
        requireText(version, "version");
        if (digest == null || !digest.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("digest must be a lowercase SHA-256 hex value");
        }
        requireText(institutionId, "institutionId");
        requireText(subjectRefDigest, "subjectRefDigest");
        requireText(workloadId, "workloadId");
        requireText(purpose, "purpose");
        requireText(approvedPolicySnapshotId, "approvedPolicySnapshotId");
        requireText(approvedDestinationProfileId, "approvedDestinationProfileId");
        requireText(approvedDestination, "approvedDestination");
        requireText(approvedBeneficiaryReference, "approvedBeneficiaryReference");
        if (approvedAsset == null || (approvedAmount == null && approvedAmountLimit == null)
            || (approvedAmount != null && approvedAmount.atomicUnits().signum() <= 0)
            || (approvedAmountLimit != null && approvedAmountLimit.atomicUnits().signum() <= 0)) {
            throw new IllegalArgumentException("approved transaction terms are incomplete");
        }
        if (approvedAmount != null && approvedAmountLimit != null
            && approvedAmount.compareTo(approvedAmountLimit) > 0) {
            throw new IllegalArgumentException("approvedAmount exceeds approvedAmountLimit");
        }
        if (approvedFrom == null || approvedUntil == null || approvedFrom.isAfter(approvedUntil)) {
            throw new IllegalArgumentException("approved period is invalid");
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
