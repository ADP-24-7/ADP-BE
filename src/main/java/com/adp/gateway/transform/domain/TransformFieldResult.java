package com.adp.gateway.transform.domain;

import com.adp.gateway.retrieval.domain.DataClass;
import com.fasterxml.jackson.annotation.JsonIgnore;

public record TransformFieldResult(
    String path,
    String datasetName,
    String fieldName,
    DataClass dataClass,
    TransformStrategy strategy,
    String strategyVersion,
    String keyVersion,
    String mappingVersion,
    String instructionDigest,
    String sourceValueDigest,
    String transformedValueDigest,
    String tokenRef,
    @JsonIgnore
    Object transformedValue
) {
}
