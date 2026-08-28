package com.adp.gateway.policy.domain;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

public record PolicySnapshot(
    String policyVersion,
    String snapshotDigest,
    OffsetDateTime effectiveAt,
    PolicyLifecycleStage lifecycleStage,
    PolicyArtifact sourceArtifact,
    PolicyEvaluation evaluation
) {

    public PolicySnapshot {
        Objects.requireNonNull(policyVersion, "policyVersion must not be null");
        Objects.requireNonNull(snapshotDigest, "snapshotDigest must not be null");
        Objects.requireNonNull(sourceArtifact, "sourceArtifact must not be null");
        Objects.requireNonNull(evaluation, "evaluation must not be null");
        if (!policyVersion.equals(evaluation.policyVersion())) {
            throw new IllegalArgumentException("PolicySnapshot and PolicyEvaluation versions must match");
        }
        if (!snapshotDigest.equals(evaluation.snapshotDigest())) {
            throw new IllegalArgumentException("PolicySnapshot and PolicyEvaluation digests must match");
        }
        if (!policyVersion.equals(sourceArtifact.artifactVersion())) {
            throw new IllegalArgumentException("PolicySnapshot and source artifact versions must match");
        }
        if (!snapshotDigest.equals(sourceArtifact.digest())) {
            throw new IllegalArgumentException("PolicySnapshot and source artifact digests must match");
        }
    }

    public List<String> matchedRuleIds() {
        return evaluation.matchedRuleIds();
    }
}
