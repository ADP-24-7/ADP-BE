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
        String canonical = String.join("|",
            value(policyContext.workloadId()),
            value(policyContext.purpose()),
            value(policyContext.provider()),
            dataClass.name()
        );
        return new TransformScope(
            hasher.hash(canonical),
            policyContext.workloadId(),
            policyContext.purpose(),
            policyContext.provider(),
            decision.policyVersion(),
            decision.snapshotDigest(),
            dataClass
        );
    }

    private static String value(String value) {
        return value == null ? "<none>" : value;
    }
}
