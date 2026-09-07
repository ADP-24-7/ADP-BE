package com.adp.gateway.ai.application;

import java.util.List;
import java.util.Set;

import com.adp.gateway.ai.domain.AiEvaluationBundleSource;

public interface AiEvaluationBundlePort {
    List<AiEvaluationBundleSource> load(
        String evaluationRunId,
        String institutionId,
        Set<String> allowedWorkloads,
        int limit
    );
}
