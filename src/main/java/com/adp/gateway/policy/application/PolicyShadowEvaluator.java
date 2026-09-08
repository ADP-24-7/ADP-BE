package com.adp.gateway.policy.application;

import com.adp.gateway.policy.domain.PolicyLifecycleRecord;
import com.adp.gateway.policy.domain.PolicyShadowOutcome;

public interface PolicyShadowEvaluator {
    PolicyShadowOutcome evaluate(PolicyLifecycleRecord artifact, String evaluationCaseId);
}
