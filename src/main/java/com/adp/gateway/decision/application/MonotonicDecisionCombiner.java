package com.adp.gateway.decision.application;

import com.adp.gateway.decision.domain.FinalAction;
import com.adp.gateway.policy.domain.PolicyAction;
import org.springframework.stereotype.Component;

@Component
public class MonotonicDecisionCombiner {

    public FinalAction combine(PolicyAction policyAction, FinalAction runtimeAction) {
        FinalAction policyFinalAction = toFinalAction(policyAction);
        return policyFinalAction.isAtLeastAsRestrictiveAs(runtimeAction) ? policyFinalAction : runtimeAction;
    }

    private FinalAction toFinalAction(PolicyAction policyAction) {
        return switch (policyAction) {
            case ALLOW -> FinalAction.ALLOW;
            case TRANSFORM -> FinalAction.TRANSFORM;
            case REVIEW -> FinalAction.REVIEW;
            case BLOCK -> FinalAction.BLOCK;
        };
    }
}
