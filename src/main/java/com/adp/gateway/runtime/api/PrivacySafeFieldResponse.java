package com.adp.gateway.runtime.api;

import com.adp.gateway.transform.domain.TransformFieldResult;

public record PrivacySafeFieldResponse(
    String path,
    String datasetName,
    String fieldName,
    String dataClass,
    String strategy,
    String sourceValueDigest,
    String transformedValueDigest,
    String tokenRef
) {

    public static PrivacySafeFieldResponse from(TransformFieldResult field) {
        return new PrivacySafeFieldResponse(
            field.path(),
            field.datasetName(),
            field.fieldName(),
            field.dataClass().name(),
            field.strategy().name(),
            field.sourceValueDigest(),
            field.transformedValueDigest(),
            field.tokenRef()
        );
    }
}
