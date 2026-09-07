package com.adp.gateway.policy.application;

import java.util.Map;
import java.util.Set;

import com.adp.gateway.policy.domain.PolicyLifecycleStage;
import com.adp.gateway.policy.domain.PolicyLifecycleTransitionReason;
import org.springframework.stereotype.Component;

@Component
public class PolicyLifecycleTransitionValidator {
    private static final Map<PolicyLifecycleStage, Set<PolicyLifecycleStage>> TRANSITIONS = Map.of(
        PolicyLifecycleStage.DRAFT, Set.of(PolicyLifecycleStage.VALIDATED),
        PolicyLifecycleStage.VALIDATED, Set.of(PolicyLifecycleStage.CANDIDATE),
        PolicyLifecycleStage.CANDIDATE, Set.of(PolicyLifecycleStage.REPLAY),
        PolicyLifecycleStage.REPLAY, Set.of(PolicyLifecycleStage.SHADOW),
        PolicyLifecycleStage.SHADOW, Set.of(PolicyLifecycleStage.APPROVED),
        PolicyLifecycleStage.APPROVED, Set.of(PolicyLifecycleStage.ACTIVE),
        PolicyLifecycleStage.ACTIVE, Set.of(PolicyLifecycleStage.REVIEW, PolicyLifecycleStage.ROLLED_BACK),
        PolicyLifecycleStage.REVIEW, Set.of(PolicyLifecycleStage.ROLLED_BACK)
    );

    public void validate(
        PolicyLifecycleStage from,
        PolicyLifecycleStage to,
        PolicyLifecycleTransitionReason reason
    ) {
        if (!TRANSITIONS.getOrDefault(from, Set.of()).contains(to) || !reason.supports(to)) {
            throw new PolicyLifecycleException("POLICY_LIFECYCLE_TRANSITION_INVALID");
        }
    }
}
