package com.adp.gateway.policy.application;

import com.adp.gateway.policy.domain.PolicyEvaluation;
import com.adp.gateway.policy.domain.SourcePolicyEvaluationArtifactRef;

public record NormalizedPolicyEvaluation(
    SourcePolicyEvaluationArtifactRef sourcePolicyEvaluationArtifactRef,
    String handoffDisposition,
    PolicyEvaluation evaluation
) {
}
