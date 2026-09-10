package com.adp.gateway.policy.domain;

import java.util.List;

public record PolicyArtifactHistory(
    PolicyLifecycleRecord artifact,
    boolean currentSelection,
    List<PolicyLifecycleTransitionEvent> transitions,
    long transitionTotal,
    boolean transitionHasMore,
    List<PolicyShadowEvidence> shadowEvaluations,
    long shadowTotal,
    boolean shadowHasMore
) {
    public PolicyArtifactHistory {
        transitions = List.copyOf(transitions);
        shadowEvaluations = List.copyOf(shadowEvaluations);
    }
}
