package com.adp.gateway.policy.domain;

import java.util.List;

import com.adp.gateway.retrieval.domain.DataClass;

public record RuntimePolicyContext(
    String workloadId,
    String purpose,
    String subjectType,
    String subjectRefDigest,
    String canonicalContextDigest,
    List<DataClass> runtimeDataClasses,
    String processingContext,
    String provider,
    String runtimeContextDigest
) {
}
