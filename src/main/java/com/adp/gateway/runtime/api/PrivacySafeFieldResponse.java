package com.adp.gateway.runtime.api;

import com.adp.gateway.transform.domain.TransformFieldResult;

public record PrivacySafeFieldResponse(
    String path,
    String dataClass,
    String strategy
) {

    public static PrivacySafeFieldResponse from(TransformFieldResult field) {
        return new PrivacySafeFieldResponse(
            field.path(),
            field.dataClass().name(),
            field.strategy().name()
        );
    }
}
