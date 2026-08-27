package com.adp.gateway.retrieval.domain;

public record RetrievalDatasetScope(
    String datasetName,
    int rowLimit,
    Integer timeWindowDays
) {
}
