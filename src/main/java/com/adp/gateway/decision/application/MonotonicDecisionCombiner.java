package com.adp.gateway.decision.application;

import com.adp.gateway.decision.domain.DecisionAction;
import org.springframework.stereotype.Component;

@Component
public class MonotonicDecisionCombiner {

    public DecisionAction combine(DecisionAction policyAction, DecisionAction runtimeAction) {
        return policyAction.isAtLeastAsRestrictiveAs(runtimeAction) ? policyAction : runtimeAction;
    }
}
