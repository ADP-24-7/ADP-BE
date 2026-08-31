package com.adp.gateway.policy.application;

import com.adp.gateway.policy.domain.ApplicabilityResult;
import com.adp.gateway.policy.domain.PolicySnapshot;
import com.adp.gateway.policy.domain.RuntimePolicyContext;

public interface PolicyApplicabilityEvaluator {

    ApplicabilityResult evaluate(PolicySnapshot snapshot, RuntimePolicyContext context);
}
