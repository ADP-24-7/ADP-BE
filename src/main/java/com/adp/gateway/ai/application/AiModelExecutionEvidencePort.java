package com.adp.gateway.ai.application;

import com.adp.gateway.ai.domain.AiModelProfile;
import com.adp.gateway.ai.domain.AiEvaluationRunDefinition;

public interface AiModelExecutionEvidencePort {

    void record(
        String executionId,
        AiModelProfile profile,
        AiEvaluationRunDefinition evaluationRun,
        String evalCaseId,
        String expectedInputDigest,
        String actualInputDigest,
        String destinationProfileDigest
    );
}
