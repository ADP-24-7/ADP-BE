package com.adp.gateway.transform.application;

import com.adp.gateway.context.application.CanonicalValueHasher;
import com.adp.gateway.decision.domain.RuntimeDecision;
import com.adp.gateway.policy.domain.RuntimePolicyContext;
import com.adp.gateway.retrieval.domain.DataClass;

public record TransformScope(
    String scopeId,
    String workloadId,
    String purposeCode,
    String providerProfileId,
    String policyVersion,
    String snapshotDigest,
    DataClass dataClass
) {

    public static TransformScope from(
        RuntimePolicyContext policyContext,
        RuntimeDecision decision,
        DataClass dataClass,
        CanonicalValueHasher hasher
    ) {
        return from(policyContext, decision, dataClass, hasher, null);
    }

    public static TransformScope from(RuntimePolicyContext policyContext, RuntimeDecision decision,
        DataClass dataClass, CanonicalValueHasher hasher, String evaluationScope) {
        String canonical = String.join("|",
            value(policyContext.workloadId()),
            value(policyContext.purpose()),
            value(evaluationScope == null ? policyContext.provider() : evaluationScope),
            dataClass.name()
        );
        return new TransformScope(
            hasher.hash(canonical),
            policyContext.workloadId(),
            policyContext.purpose(),
            evaluationScope == null ? policyContext.provider() : evaluationScope,
            decision.policyVersion(),
            decision.snapshotDigest(),
            dataClass
        );
    }

    private static String value(String value) {
        return value == null ? "<none>" : value;
    }
}
