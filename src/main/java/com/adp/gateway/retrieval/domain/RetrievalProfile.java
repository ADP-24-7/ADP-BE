package com.adp.gateway.retrieval.domain;

import java.util.List;

public record RetrievalProfile(
    String profileId,
    String workloadId,
    String purpose,
    String subjectType,
    int timeWindowDays,
    int rowLimit,
    List<RetrievalField> fields
) {

    public boolean allowsField(String datasetName, String fieldName) {
        return fields.stream()
            .anyMatch(field -> field.datasetName().equals(datasetName) && field.fieldName().equals(fieldName));
    }
}
