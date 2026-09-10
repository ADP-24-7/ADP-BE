package com.adp.gateway.dataaccess.application;

import java.time.LocalDate;

import com.adp.gateway.auth.domain.SubjectRef;

public record DataAccessRequest(
    String requestId,
    String traceId,
    String workloadId,
    String purpose,
    SubjectRef subject,
    LocalDate asOfDate
) {
    public DataAccessRequest(
        String requestId,
        String traceId,
        String workloadId,
        String purpose,
        SubjectRef subject
    ) {
        this(requestId, traceId, workloadId, purpose, subject, null);
    }
}
