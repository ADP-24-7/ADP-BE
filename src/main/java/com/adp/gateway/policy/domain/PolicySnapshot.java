package com.adp.gateway.policy.domain;

import java.time.OffsetDateTime;
import java.util.List;

public record PolicySnapshot(
    String policyVersion,
    String snapshotDigest,
    OffsetDateTime effectiveAt,
    PolicyLifecycleStage lifecycleStage,
    PolicyArtifact sourceArtifact,
    PolicyEvaluation evaluation
) {

    public List<String> matchedRuleIds() {
        return evaluation.matchedRuleIds();
    }
}
