package com.adp.gateway.ai.application;

import java.time.OffsetDateTime;
import java.util.Set;

import com.adp.gateway.ai.domain.AiOperationsOverview;

public interface AiOperationsOverviewPort {
    AiOperationsOverview load(
        String institutionId,
        Set<String> allowedWorkloads,
        OffsetDateTime from,
        OffsetDateTime to,
        OffsetDateTime generatedAt,
        String executionQuery,
        String executionStatus,
        int executionPage,
        int executionSize
    );
}
