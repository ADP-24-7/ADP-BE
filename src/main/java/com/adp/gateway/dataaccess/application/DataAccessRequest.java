package com.adp.gateway.dataaccess.application;

import com.adp.gateway.auth.domain.SubjectRef;

public record DataAccessRequest(
    String requestId,
    String traceId,
    String workloadId,
    String purpose,
    SubjectRef subject
) {
}
