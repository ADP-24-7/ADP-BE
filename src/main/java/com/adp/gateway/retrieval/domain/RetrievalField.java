package com.adp.gateway.retrieval.domain;

public record RetrievalField(
    String datasetName,
    String fieldName,
    DataClass dataClass
) {

    public String qualifiedName() {
        return datasetName + "." + fieldName;
    }
}
