package com.adp.gateway.operations.api;

import java.util.List;
import java.util.Map;

import com.adp.gateway.retrieval.domain.RetrievalResult;

public record DataAccessPreviewResponse(
    String dataAccessId,
    String workloadId,
    String purpose,
    String subjectType,
    String subjectId,
    String profileId,
    int rowCount,
    List<DatasetScopeResponse> datasetScopes,
    List<FieldResponse> selectedFields,
    List<RecordResponse> records
) {

    public static DataAccessPreviewResponse from(RetrievalResult result) {
        return new DataAccessPreviewResponse(
            result.dataAccessId(),
            result.workloadId(),
            result.purpose(),
            result.subjectType(),
            result.subjectId(),
            result.profileId(),
            result.rowCount(),
            result.datasetScopes().stream()
                .map(scope -> new DatasetScopeResponse(
                    scope.datasetName(),
                    scope.rowLimit(),
                    scope.timeWindowDays()
                ))
                .toList(),
            result.selectedFields().stream()
                .map(field -> new FieldResponse(
                    field.datasetName(),
                    field.fieldName(),
                    field.dataClass().name()
                ))
                .toList(),
            result.records().stream()
                .map(record -> new RecordResponse(record.datasetName(), record.fields()))
                .toList()
        );
    }

    public record FieldResponse(
        String datasetName,
        String fieldName,
        String dataClass
    ) {
    }

    public record DatasetScopeResponse(
        String datasetName,
        int rowLimit,
        Integer timeWindowDays
    ) {
    }

    public record RecordResponse(
        String datasetName,
        Map<String, Object> fields
    ) {
    }
}
