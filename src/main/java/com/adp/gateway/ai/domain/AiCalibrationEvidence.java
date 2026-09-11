package com.adp.gateway.ai.domain;

import java.time.OffsetDateTime;
import java.util.List;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AiCalibrationEvidence(
    Manifest manifest,
    boolean calibrationReady,
    List<String> readinessReasonCodes,
    List<ExecutionEvidence> executions
) {
    public AiCalibrationEvidence {
        readinessReasonCodes = List.copyOf(readinessReasonCodes);
        executions = List.copyOf(executions);
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Manifest(
        String schemaVersion,
        String contentDigest,
        String evaluationRunId,
        String evaluationRunVersion,
        int executionCount,
        OffsetDateTime generatedAt,
        OffsetDateTime executionFrom,
        OffsetDateTime executionCutoffAt
    ) { }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ExecutionEvidence(
        String executionId,
        String evalCaseId,
        String modelProfileId,
        String responseGuardStatus,
        String controlledDeliveryStatus,
        List<String> reasonCodes,
        String detectorVersion,
        int findingCount,
        int observedFindingCount,
        int missingReflectionMetadataCount,
        List<FindingGroup> findingGroups
    ) {
        public ExecutionEvidence {
            reasonCodes = List.copyOf(reasonCodes);
            findingGroups = List.copyOf(findingGroups);
        }
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record FindingGroup(
        String findingType,
        String sourceDataClass,
        String transformStrategy,
        String fieldTreatment,
        int count,
        List<String> evidenceDigests
    ) {
        public FindingGroup {
            evidenceDigests = List.copyOf(evidenceDigests);
        }
    }
}
