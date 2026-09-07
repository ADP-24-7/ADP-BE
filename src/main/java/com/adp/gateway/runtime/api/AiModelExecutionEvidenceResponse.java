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
    String samplingProfileVersion,
    String evaluationRunId,
    String evaluationRunVersion,
    String evalCaseId,
    String evaluationContractDigest,
    String expectedInputDigest,
    String actualInputDigest,
    String datasetId,
    String datasetVersion,
    String datasetDigest,
    String policySnapshotDigest,
    String destinationProfileDigest,
    String measurementType,
    Long fullResponseLatencyMillis,
    Long attemptElapsedMillis,
    Integer inputTokens,
    Integer outputTokens,
    Integer totalTokens,
    String tokenUsageStatus,
    Long initialRuntimeLatencyMillis,
    String runtimeFinalAction,
    String responseGuardStatus,
    String providerStatus,
    String errorCategory,
    String traceReference
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
            trace.aiSamplingProfileVersion(),
            trace.aiEvaluationRunId(),
            trace.aiEvaluationRunVersion(),
            trace.aiEvalCaseId(),
            trace.aiEvaluationContractDigest(),
            trace.aiExpectedInputDigest(),
            trace.aiActualInputDigest(),
            trace.aiDatasetId(),
            trace.aiDatasetVersion(),
            trace.aiDatasetDigest(),
            trace.aiPolicySnapshotDigest(),
            trace.aiDestinationProfileDigest(),
            trace.aiMeasurementType(),
            trace.aiFullResponseLatencyMillis(),
            trace.aiAttemptElapsedMillis(),
            trace.aiInputTokens(),
            trace.aiOutputTokens(),
            trace.aiTotalTokens(),
            trace.aiTokenUsageStatus(),
            trace.aiInitialRuntimeLatencyMillis(),
            trace.finalAction(),
            trace.responseGuardStatus(),
            trace.aiProviderStatus(),
            trace.aiErrorCategory(),
            trace.executionId()
        );
    }
}
