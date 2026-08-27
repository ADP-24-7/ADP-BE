package com.adp.gateway.retrieval.domain;

import java.util.List;

public record RetrievalProfile(
    String profileId,
    String workloadId,
    String purpose,
    String subjectType,
    List<RetrievalDatasetScope> datasetScopes,
    List<RetrievalField> fields
) {

    public boolean allowsField(String datasetName, String fieldName) {
        return fields.stream()
            .anyMatch(field -> field.datasetName().equals(datasetName) && field.fieldName().equals(fieldName));
    }

    public List<RetrievalField> fieldsFor(String datasetName) {
        return fields.stream()
            .filter(field -> field.datasetName().equals(datasetName))
            .toList();
    }

    public java.util.Optional<RetrievalDatasetScope> scopeFor(String datasetName) {
        return datasetScopes.stream()
            .filter(scope -> scope.datasetName().equals(datasetName))
            .findFirst();
    }
}
