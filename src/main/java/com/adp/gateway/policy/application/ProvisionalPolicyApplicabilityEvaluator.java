package com.adp.gateway.policy.application;

import com.adp.gateway.policy.domain.ApplicabilityResult;
import com.adp.gateway.policy.domain.PolicySnapshot;
import com.adp.gateway.policy.domain.RuntimePolicyContext;
import com.adp.gateway.retrieval.domain.DataClass;
import org.springframework.stereotype.Component;

@Component
public class ProvisionalPolicyApplicabilityEvaluator implements PolicyApplicabilityEvaluator {

    @Override
    public ApplicabilityResult evaluate(PolicySnapshot snapshot, RuntimePolicyContext context) {
        if (!snapshot.sourceArtifact().workloadId().equals(context.workloadId())) {
            return ApplicabilityResult.NOT_APPLICABLE;
        }
        if (snapshot.matchedRuleIds().isEmpty()) {
            return ApplicabilityResult.INCOMPLETE;
        }
        if (context.canonicalContextDigest() == null || context.runtimeDataClasses().isEmpty()) {
            return ApplicabilityResult.INCOMPLETE;
        }
        if (context.runtimeDataClasses().contains(DataClass.UNKNOWN)) {
            return ApplicabilityResult.INCOMPLETE;
        }
        return ApplicabilityResult.APPLICABLE;
    }
}
