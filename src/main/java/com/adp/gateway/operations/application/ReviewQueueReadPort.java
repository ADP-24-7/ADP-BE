package com.adp.gateway.operations.application;

import java.util.Set;

import com.adp.gateway.egress.domain.ExecutionPackType;
import com.adp.gateway.operations.domain.ReviewQueueDetail;
import com.adp.gateway.operations.domain.ReviewQueuePage;

public interface ReviewQueueReadPort {
    ReviewQueuePage search(
        String institutionId,
        Set<String> allowedWorkloads,
        ExecutionPackType executionPack,
        String workloadId,
        int page,
        int size
    );

    ReviewQueueDetail load(String executionId, String institutionId, Set<String> allowedWorkloads);
}
