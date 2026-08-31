package com.adp.gateway.policy.domain;

import java.util.List;
import java.util.Objects;

import com.adp.gateway.retrieval.domain.DataClass;

public record RuntimePolicyContext(
    String workloadId,
    String purpose,
    String subjectType,
    String subjectRefDigest,
    String canonicalContextDigest,
    List<DataClass> runtimeDataClasses,
    List<String> processingContexts,
    String provider,
    String runtimeContextDigest
) {

    public RuntimePolicyContext {
        Objects.requireNonNull(workloadId, "workloadId must not be null");
        Objects.requireNonNull(purpose, "purpose must not be null");
        Objects.requireNonNull(runtimeContextDigest, "runtimeContextDigest must not be null");
        runtimeDataClasses = List.copyOf(runtimeDataClasses);
        processingContexts = List.copyOf(processingContexts);
    }
}
