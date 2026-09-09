package com.adp.gateway.policy.domain;

import java.time.OffsetDateTime;

public record PolicyLifecycleTransitionEvent(
    long transitionId,
    PolicyLifecycleStage fromStage,
    PolicyLifecycleStage toStage,
    String actorId,
    PolicyLifecycleTransitionReason reasonCode,
    String artifactDigest,
    String approvalGateVersion,
    String shadowEvaluationId,
    OffsetDateTime occurredAt
) {
}
