package com.adp.gateway.digitalasset.application;

import com.adp.gateway.digitalasset.domain.ApprovedTransactionReference;

public record ApprovedTransactionLookup(
    ApprovedTransactionReference reference,
    String institutionId,
    String subjectRefDigest,
    String workloadId,
    String purpose
) {
    public ApprovedTransactionLookup {
        if (reference == null) {
            throw new IllegalArgumentException("approved transaction reference is required");
        }
        requireText(institutionId, "institutionId");
        requireText(subjectRefDigest, "subjectRefDigest");
        requireText(workloadId, "workloadId");
        requireText(purpose, "purpose");
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
