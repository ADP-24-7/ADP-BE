package com.adp.gateway.policyharness.domain;

import java.util.Set;

public record FieldLineage(
    Set<String> requestedFields,
    Set<String> retrievedFields,
    Set<String> transformedFields,
    Set<String> releasedFields,
    String requestedFieldsDigest,
    String retrievedFieldsDigest,
    String transformedFieldsDigest,
    String releasedFieldsDigest
) {

    public FieldLineage {
        requestedFields = Set.copyOf(requestedFields);
        retrievedFields = Set.copyOf(retrievedFields);
        transformedFields = Set.copyOf(transformedFields);
        releasedFields = Set.copyOf(releasedFields);
    }
}
