package com.adp.gateway.ai.application;

import com.adp.gateway.ai.domain.AiModelProfile;

public interface AiModelExecutionEvidencePort {

    void record(String executionId, AiModelProfile profile);
}
