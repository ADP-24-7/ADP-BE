package com.adp.gateway.audit.application;

import java.time.OffsetDateTime;
import java.util.Set;

import com.adp.gateway.audit.domain.AuditExecutionPage;
import com.adp.gateway.audit.domain.ExecutionEvidencePack;

public interface AuditReadPort {
    AuditExecutionPage search(
        String institutionId,
        Set<String> allowedWorkloads,
        String workloadId,
        String status,
        OffsetDateTime from,
        OffsetDateTime to,
        int page,
        int size
    );

    ExecutionEvidencePack loadEvidence(String executionId, String institutionId, Set<String> allowedWorkloads);
}
