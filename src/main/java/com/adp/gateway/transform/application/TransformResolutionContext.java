package com.adp.gateway.transform.application;

import com.adp.gateway.retrieval.domain.DataClass;

public record TransformResolutionContext(
    String workloadId,
    String purposeCode,
    String providerProfileId,
    String policyVersion,
    String snapshotDigest,
    DataClass dataClass,
    String fieldPath
) {
}
