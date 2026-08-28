package com.adp.gateway.decision.domain;

import java.util.List;

import com.adp.gateway.common.error.ReasonCode;
import com.adp.gateway.policy.domain.ApplicabilityResult;

public record RuntimeDecision(
    String decisionId,
    DecisionAction policyAction,
    DecisionAction finalAction,
    List<ReasonCode> runtimeReasonCodes,
    RuntimeAuthorizationResult authorizationResult,
    ApplicabilityResult applicabilityResult,
    List<String> matchedRuleIds,
    List<String> evidenceRefs,
    List<String> requiredControls,
    String policyVersion,
    String snapshotDigest,
    String runtimeContextDigest,
    String sourceArtifactId
) {

    public ReasonCode primaryReasonCode() {
        return runtimeReasonCodes().isEmpty() ? ReasonCode.INTERNAL_ERROR : runtimeReasonCodes().getFirst();
    }
}
