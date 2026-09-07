package com.adp.gateway.ai.domain;

public record AiEvaluationCaseDefinition(
    String caseId,
    String datasetRowRef,
    String inputSchemaVersion,
    String expectedInputDigest
) {
    private static final String SHA256_PATTERN = "[0-9a-f]{64}";

    public AiEvaluationCaseDefinition {
        requireNonBlank(caseId, "caseId");
        requireNonBlank(datasetRowRef, "datasetRowRef");
        requireNonBlank(inputSchemaVersion, "inputSchemaVersion");
        if (expectedInputDigest == null || !expectedInputDigest.matches(SHA256_PATTERN)) {
            throw new IllegalArgumentException("expectedInputDigest must be a SHA-256 hex digest");
        }
    }

    private static void requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
