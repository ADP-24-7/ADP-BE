package com.adp.gateway.policy.domain;

import java.util.List;

import com.adp.gateway.decision.domain.DecisionAction;

public record PolicyEvaluation(
    List<String> matchedRuleIds,
    List<String> evidenceRefs,
    DecisionAction policyAction,
    List<String> requiredControls,
    String policyVersion,
    String snapshotDigest
) {
}
