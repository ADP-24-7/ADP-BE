package com.adp.gateway.transform.domain;

import com.adp.gateway.retrieval.domain.DataClass;
import com.fasterxml.jackson.annotation.JsonIgnore;

public record TransformFieldResult(
    String path,
    String datasetName,
    String fieldName,
    DataClass dataClass,
    TransformStrategy strategy,
    String sourceValueDigest,
    String transformedValueDigest,
    String tokenRef,
    @JsonIgnore
    Object transformedValue
) {
}
