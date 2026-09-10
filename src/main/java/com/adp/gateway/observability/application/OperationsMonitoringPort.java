package com.adp.gateway.observability.application;

import java.time.OffsetDateTime;
import java.util.Set;

import com.adp.gateway.observability.domain.OperationalMetricSnapshot;
import com.adp.gateway.observability.domain.OperationsSummary;
import com.adp.gateway.egress.domain.ExecutionPackType;
import com.adp.gateway.observability.domain.PolicyOperationEvent.PolicyEventCategory;
import com.adp.gateway.observability.domain.PolicyOperationEventPage;

public interface OperationsMonitoringPort {

    OperationsSummary loadSummary(
        String institutionId,
        Set<String> allowedWorkloads,
        ExecutionPackType executionPack,
        OffsetDateTime windowStart,
        OffsetDateTime now,
        int windowMinutes
    );

    PolicyOperationEventPage loadPolicyEvents(
        String institutionId,
        Set<String> allowedWorkloads,
        ExecutionPackType executionPack,
        String workloadId,
        PolicyEventCategory category,
        OffsetDateTime from,
        OffsetDateTime to,
        int page,
        int size
    );

    OperationalMetricSnapshot loadGlobalMetricSnapshot(OffsetDateTime now);
}
