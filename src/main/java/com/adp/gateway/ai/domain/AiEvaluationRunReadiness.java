package com.adp.gateway.ai.domain;

import java.util.List;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AiEvaluationRunReadiness(
    String evaluationRunId,
    String evaluationRunVersion,
    Status status,
    boolean bundleAvailable,
    int expectedExecutionCount,
    long storedExecutionCount,
    int observedExecutionCount,
    int completeEvidenceCount,
    int missingExecutionCount,
    int unexpectedExecutionCount,
    List<String> reasonCodes,
    List<CaseModelEvidence> caseModels
) {
    public AiEvaluationRunReadiness {
        reasonCodes = List.copyOf(reasonCodes);
        caseModels = List.copyOf(caseModels);
    }

    public enum Status {
        NOT_STARTED,
        INCOMPLETE,
        PROVENANCE_MISMATCH,
        MODEL_MISMATCH,
        READY
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record CaseModelEvidence(
        String evalCaseId,
        String profileId,
        String executionId,
        String runtimeStatus,
        String providerStatus,
        String evidenceStatus,
        List<String> reasonCodes
    ) {
        public CaseModelEvidence {
            reasonCodes = List.copyOf(reasonCodes);
        }
    }
}
