package com.adp.gateway.operations.application;

import java.time.OffsetDateTime;
import java.util.Set;

import com.adp.gateway.egress.domain.ExecutionPackType;
import com.adp.gateway.operations.domain.SecurityFindingDetail;
import com.adp.gateway.operations.domain.SecurityFindingPage;

public interface SecurityFindingReadPort {
    SecurityFindingPage search(
        String institutionId,
        Set<String> allowedWorkloads,
        ExecutionPackType executionPack,
        String workloadId,
        String findingType,
        OffsetDateTime from,
        OffsetDateTime to,
        int page,
        int size
    );

    SecurityFindingDetail load(long findingId, String institutionId, Set<String> allowedWorkloads);
}
