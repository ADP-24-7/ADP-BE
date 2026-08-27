package com.adp.gateway.retrieval.domain;

import java.util.Map;

public record RetrievalRecord(
    String datasetName,
    Map<String, Object> fields
) {
}
