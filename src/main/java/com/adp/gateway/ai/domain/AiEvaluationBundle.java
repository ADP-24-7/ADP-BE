package com.adp.gateway.ai.domain;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AiEvaluationBundle(
    Manifest manifest,
    ExecutionConfig executionConfig,
    List<CaseResult> caseResults,
    List<RuntimeMetric> runtimeMetrics,
    FailureSummary failureSummary,
    List<TraceEntry> traceIndex
) {
    public AiEvaluationBundle {
        caseResults = List.copyOf(caseResults);
        runtimeMetrics = List.copyOf(runtimeMetrics);
        traceIndex = List.copyOf(traceIndex);
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Manifest(
        String schemaVersion,
        String bundleId,
        String bundleVersion,
        String contentDigest,
        String evaluationRunId,
        String evaluationRunVersion,
        int executionCount,
        int caseCount,
        int modelCount,
        OffsetDateTime generatedAt,
        OffsetDateTime executionFrom,
        OffsetDateTime executionCutoffAt
    ) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record FailureSummary(
        int evaluatedExecutionCount,
        int failed,
        int sentUnknown,
        int notAttempted,
        Map<String, Integer> byErrorCategory
    ) {
        public FailureSummary {
            byErrorCategory = Map.copyOf(byErrorCategory);
        }
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ExecutionConfig(
        String evaluationRunId,
        String evaluationRunVersion,
        String evaluationContractDigest,
        String datasetId,
        String datasetVersion,
        String datasetDigest,
        String policySnapshotDigest,
        List<ModelConfig> models
    ) {
        public ExecutionConfig {
            models = List.copyOf(models);
        }
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ModelConfig(
        String profileId,
        String profileVersion,
        String profileDigest,
        String providerModelId,
        String providerModelVersion,
        String connectionProfileId,
        int maxTokens,
        double temperature,
        String samplingProfileVersion,
        String destinationProfileDigest
    ) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record CaseResult(
        String executionId,
        String evalCaseId,
        String modelProfileId,
        String runtimeStatus,
        String finalAction,
        String responseGuardStatus,
        String controlledDeliveryStatus,
        String providerStatus,
        String errorCategory,
        String evidenceStatus,
        String expectedInputDigest,
        String actualInputDigest
    ) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record RuntimeMetric(
        String executionId,
        String evalCaseId,
        String modelProfileId,
        String measurementType,
        Long fullResponseLatencyMillis,
        Long attemptElapsedMillis,
        Long initialRuntimeLatencyMillis,
        Integer inputTokens,
        Integer outputTokens,
        Integer totalTokens,
        String tokenUsageStatus,
        Integer providerHttpStatus,
        String providerStatus,
        String errorCategory
    ) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record TraceEntry(
        String executionId,
        String decisionId,
        String connectorExecutionId,
        String providerRequestDigest,
        String providerResponseDigest,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
    ) {
    }
}
