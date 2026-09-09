package com.adp.gateway.policy.application;

import java.util.Set;

import com.adp.gateway.egress.domain.ExecutionPackType;
import com.adp.gateway.policy.domain.PolicyArtifactHistory;
import com.adp.gateway.policy.domain.PolicyArtifactPage;
import com.adp.gateway.policy.domain.PolicyLifecycleRecord;
import com.adp.gateway.policy.domain.PolicyLifecycleStage;

public interface PolicyOperationsReadPort {
    PolicyArtifactPage search(
        String institutionId,
        Set<String> allowedWorkloads,
        ExecutionPackType executionPack,
        PolicyLifecycleStage lifecycleStage,
        String workloadId,
        String query,
        boolean attentionRequired,
        int limit,
        int offset
    );

    PolicyArtifactHistory history(PolicyLifecycleRecord artifact);
}
