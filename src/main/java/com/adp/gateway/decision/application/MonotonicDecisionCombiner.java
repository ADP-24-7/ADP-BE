package com.adp.gateway.decision.application;

import com.adp.gateway.decision.domain.FinalAction;
import com.adp.gateway.policy.domain.PolicyAction;
import org.springframework.stereotype.Component;

@Component
public class MonotonicDecisionCombiner {

    public FinalAction combine(PolicyAction policyAction, FinalAction runtimeAction) {
        FinalAction policyFinalAction = FinalAction.valueOf(policyAction.name());
        return policyFinalAction.isAtLeastAsRestrictiveAs(runtimeAction) ? policyFinalAction : runtimeAction;
    }
}
