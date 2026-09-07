package com.adp.gateway.policy.application;

import java.time.OffsetDateTime;

import com.adp.gateway.policy.domain.PolicyLifecycleRecord;
import com.adp.gateway.policy.domain.PolicyLifecycleStage;
import com.adp.gateway.policy.domain.PolicyLifecycleTransitionReason;

public interface PolicyLifecyclePersistence {
    PolicyLifecycleRecord create(PolicyLifecycleRecord record);

    PolicyLifecycleRecord load(String artifactId, String artifactVersion);

    PolicyLifecycleRecord transition(
        PolicyLifecycleRecord current,
        PolicyLifecycleStage target,
        String actorId,
        PolicyLifecycleTransitionReason reason,
        OffsetDateTime occurredAt
    );
}
