package com.adp.gateway.digitalasset.domain;

import java.util.List;

public record DigitalAssetReconciliationAssessment(
    String result,
    List<String> mismatchedFields,
    String expectedDigest,
    String actualDigest
) {
    public DigitalAssetReconciliationAssessment {
        mismatchedFields = List.copyOf(mismatchedFields);
    }

    public boolean requiresReview() {
        return "MISMATCH".equals(result) || "CRITICAL_MISMATCH".equals(result);
    }
}
