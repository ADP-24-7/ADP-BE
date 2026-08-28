package com.adp.gateway.context.domain;

import com.adp.gateway.retrieval.domain.DataClass;
import com.fasterxml.jackson.annotation.JsonIgnore;

public record CanonicalContextField(
    String path,
    String datasetName,
    String fieldName,
    DataClass dataClass,
    @JsonIgnore
    Object value,
    String valueDigest
) {

    public boolean hasUnknownDataClass() {
        return dataClass == DataClass.UNKNOWN;
    }

    @Override
    public String toString() {
        return "CanonicalContextField[path=%s, datasetName=%s, fieldName=%s, dataClass=%s, value=<redacted>, valueDigest=%s]"
            .formatted(path, datasetName, fieldName, dataClass, valueDigest);
    }
}
