package com.adp.gateway.policy.domain;

import java.util.List;

import com.adp.gateway.retrieval.domain.DataClass;
import com.adp.gateway.egress.domain.ExecutionPackType;

public record PolicySelectionContext(
    String workloadId,
    String purposeCode,
    String providerProfileId,
    List<String> processingContexts,
    List<DataClass> runtimeDataClasses,
    String institutionId,
    ExecutionPackType executionPack
) {

    public PolicySelectionContext {
        processingContexts = List.copyOf(processingContexts);
        runtimeDataClasses = List.copyOf(runtimeDataClasses);
    }
}
