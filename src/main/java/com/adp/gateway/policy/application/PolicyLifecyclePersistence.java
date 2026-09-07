package com.adp.gateway.policy.application;

import java.time.OffsetDateTime;
import java.util.Set;

import com.adp.gateway.policy.domain.PolicyLifecycleRecord;
import com.adp.gateway.policy.domain.PolicyLifecycleStage;
import com.adp.gateway.policy.domain.PolicyLifecycleTransitionReason;

public interface PolicyLifecyclePersistence {
    PolicyLifecycleRecord create(PolicyLifecycleRecord record);

    PolicyLifecycleRecord load(
        String institutionId,
        Set<String> allowedWorkloads,
        String artifactId,
        String artifactVersion
    );

    PolicyLifecycleRecord transition(
        PolicyLifecycleRecord current,
        PolicyLifecycleStage target,
        String actorId,
        PolicyLifecycleTransitionReason reason,
        OffsetDateTime occurredAt
    );
}
