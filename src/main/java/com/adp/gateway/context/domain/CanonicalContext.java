package com.adp.gateway.context.domain;

import java.util.List;
import java.util.Map;

public record CanonicalContext(
    String schemaVersion,
    String contextId,
    String dataAccessId,
    String workloadId,
    String purpose,
    String subjectType,
    String subjectRefDigest,
    List<CanonicalContextField> fields,
    Map<String, String> trustedMetadata,
    String contextDigest
) {

    public static final String SCHEMA_VERSION = "canonical-context/v1";

    public CanonicalContext {
        fields = List.copyOf(fields);
        trustedMetadata = Map.copyOf(trustedMetadata);
    }

    public CanonicalContext(
        String schemaVersion,
        String contextId,
        String dataAccessId,
        String workloadId,
        String purpose,
        String subjectType,
        String subjectRefDigest,
        List<CanonicalContextField> fields,
        String contextDigest
    ) {
        this(
            schemaVersion, contextId, dataAccessId, workloadId, purpose, subjectType,
            subjectRefDigest, fields, Map.of(), contextDigest
        );
    }
}
