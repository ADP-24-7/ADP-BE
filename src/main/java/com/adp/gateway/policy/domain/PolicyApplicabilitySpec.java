package com.adp.gateway.policy.domain;

import java.util.List;
import java.util.Objects;

public record PolicyApplicabilitySpec(
    AnalysisStatus analysisStatus,
    AnalysisStatus applicabilityStatus,
    String scope,
    List<String> limitations,
    List<String> processingContexts,
    List<String> regulatoryDataCategories,
    RuntimeBinding runtimeBinding
) {

    public PolicyApplicabilitySpec {
        Objects.requireNonNull(analysisStatus, "analysisStatus must not be null");
        Objects.requireNonNull(applicabilityStatus, "applicabilityStatus must not be null");
        Objects.requireNonNull(runtimeBinding, "runtimeBinding must not be null");
        limitations = List.copyOf(limitations);
        processingContexts = List.copyOf(processingContexts);
        regulatoryDataCategories = List.copyOf(regulatoryDataCategories);
    }
}
