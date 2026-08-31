package com.adp.gateway.policy.domain;

import java.util.List;

import com.adp.gateway.retrieval.domain.DataClass;

public record PolicySelectionContext(
    String workloadId,
    String purposeCode,
    String providerProfileId,
    List<String> processingContexts,
    List<DataClass> runtimeDataClasses
) {

    public PolicySelectionContext {
        processingContexts = List.copyOf(processingContexts);
        runtimeDataClasses = List.copyOf(runtimeDataClasses);
    }
}
