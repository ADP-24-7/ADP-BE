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

    @Override
    public String toString() {
        return "TransformFieldResult[path=%s, datasetName=%s, fieldName=%s, dataClass=%s, strategy=%s, strategyVersion=%s, keyVersion=%s, mappingVersion=%s, instructionDigest=%s, sourceValueDigest=%s, transformedValueDigest=%s, tokenRef=%s, transformedValue=<redacted>]"
            .formatted(
                path,
                datasetName,
                fieldName,
                dataClass,
                strategy,
                strategyVersion,
                keyVersion,
                mappingVersion,
                instructionDigest,
                sourceValueDigest,
                transformedValueDigest,
                tokenRef
            );
    }
}
