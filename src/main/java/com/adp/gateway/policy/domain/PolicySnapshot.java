package com.adp.gateway.policy.domain;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

public record PolicySnapshot(
    String policyVersion,
    String snapshotDigest,
    OffsetDateTime effectiveAt,
    PolicyLifecycleStage lifecycleStage,
    SourcePolicyEvaluationArtifactRef sourcePolicyEvaluationArtifactRef,
    PolicyEvaluation evaluation
) {

    public PolicySnapshot {
        Objects.requireNonNull(policyVersion, "policyVersion must not be null");
        Objects.requireNonNull(snapshotDigest, "snapshotDigest must not be null");
        Objects.requireNonNull(sourcePolicyEvaluationArtifactRef, "sourcePolicyEvaluationArtifactRef must not be null");
        Objects.requireNonNull(evaluation, "evaluation must not be null");
    }

    public List<ArtifactReference> matchedRuleRefs() {
        return evaluation.matchedRuleRefs();
    }
}
