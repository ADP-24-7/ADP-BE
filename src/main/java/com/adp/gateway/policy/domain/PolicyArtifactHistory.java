package com.adp.gateway.policy.domain;

import java.util.List;

public record PolicyArtifactHistory(
    PolicyLifecycleRecord artifact,
    boolean currentSelection,
    List<PolicyLifecycleTransitionEvent> transitions,
    List<PolicyShadowEvidence> shadowEvaluations
) {
    public PolicyArtifactHistory {
        transitions = List.copyOf(transitions);
        shadowEvaluations = List.copyOf(shadowEvaluations);
    }
}
