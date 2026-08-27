package com.adp.gateway.context.domain;

import java.util.List;

public record CanonicalContext(
    String contextId,
    String dataAccessId,
    String workloadId,
    String purpose,
    String subjectType,
    String subjectRefDigest,
    List<CanonicalContextField> fields,
    String contextDigest
) {
}
