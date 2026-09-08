package com.adp.gateway.policy.infrastructure;

import com.adp.gateway.policy.application.PolicyLifecycleException;
import com.adp.gateway.policy.application.PolicyShadowEvaluator;
import com.adp.gateway.policy.domain.PolicyLifecycleRecord;
import com.adp.gateway.policy.domain.PolicyShadowOutcome;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "adp.local-fixtures.enabled", havingValue = "false", matchIfMissing = true)
public class UnconfiguredPolicyShadowEvaluator implements PolicyShadowEvaluator {
    @Override
    public PolicyShadowOutcome evaluate(PolicyLifecycleRecord artifact, String evaluationCaseId) {
        throw new PolicyLifecycleException("POLICY_SHADOW_EVALUATOR_NOT_CONFIGURED");
    }
}
