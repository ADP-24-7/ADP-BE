package com.adp.gateway.context.domain;

import com.adp.gateway.retrieval.domain.DataClass;

public record CanonicalContextField(
    String path,
    String datasetName,
    String fieldName,
    DataClass dataClass,
    Object value,
    String valueDigest
) {

    public boolean hasUnknownDataClass() {
        return dataClass == DataClass.UNKNOWN;
    }
}
