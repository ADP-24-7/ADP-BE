package com.adp.gateway.runtime.api;

import com.adp.gateway.runtime.domain.RuntimeExecutionTrace;

public record AiModelExecutionEvidenceResponse(
    String profileId,
    String profileVersion,
    String profileDigest,
    String providerModelId,
    String providerModelVersion,
    String connectionProfileId,
    Integer maxTokens,
    Double temperature,
    String samplingProfileVersion
) {

    public static AiModelExecutionEvidenceResponse from(RuntimeExecutionTrace trace) {
        if (trace.aiModelProfileId() == null) {
            return null;
        }
        return new AiModelExecutionEvidenceResponse(
            trace.aiModelProfileId(),
            trace.aiModelProfileVersion(),
            trace.aiModelProfileDigest(),
            trace.aiProviderModelId(),
            trace.aiProviderModelVersion(),
            trace.aiConnectionProfileId(),
            trace.aiMaxTokens(),
            trace.aiTemperature(),
            trace.aiSamplingProfileVersion()
        );
    }
}
