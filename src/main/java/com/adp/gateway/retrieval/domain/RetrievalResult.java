package com.adp.gateway.retrieval.domain;

import java.util.List;

public record RetrievalResult(
    String dataAccessId,
    String workloadId,
    String purpose,
    String subjectType,
    String subjectId,
    String profileId,
    int rowLimit,
    int rowCount,
    List<RetrievalField> selectedFields,
    List<RetrievalRecord> records
) {
}
