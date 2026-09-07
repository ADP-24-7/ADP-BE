package com.adp.gateway.ai.domain;

public record AiEvaluationCaseDefinition(
    String caseId,
    String datasetRowRef,
    String inputSchemaVersion,
    String expectedInputDigest
) {
}
