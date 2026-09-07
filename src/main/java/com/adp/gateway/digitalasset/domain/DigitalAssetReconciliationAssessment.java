package com.adp.gateway.digitalasset.domain;

import java.util.List;

public record DigitalAssetReconciliationAssessment(
    DigitalAssetReconciliationResult result,
    List<DigitalAssetMismatchField> mismatchedFields,
    String expectedProjectionDigest,
    String actualProjectionDigest
) {
    public DigitalAssetReconciliationAssessment {
        mismatchedFields = List.copyOf(mismatchedFields);
    }

    public boolean requiresReview() {
        return result == DigitalAssetReconciliationResult.MISMATCH
            || result == DigitalAssetReconciliationResult.CRITICAL_MISMATCH;
    }
}
