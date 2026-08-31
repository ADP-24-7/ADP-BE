package com.adp.gateway.decision.domain;

import java.util.List;

import com.adp.gateway.common.error.ReasonCode;
import com.adp.gateway.policy.domain.ApplicabilityResult;
import com.adp.gateway.policy.domain.ArtifactReference;
import com.adp.gateway.policy.domain.PolicyAction;
import com.adp.gateway.policy.domain.SourcePolicyEvaluationArtifactRef;

public record RuntimeDecision(
    String decisionId,
    PolicyAction policyAction,
    FinalAction finalAction,
    List<ReasonCode> runtimeReasonCodes,
    RuntimeAuthorizationResult authorizationResult,
    ApplicabilityResult applicabilityResult,
    List<ArtifactReference> matchedPolicyRefs,
    List<ArtifactReference> matchedRuleRefs,
    List<ArtifactReference> requirementRefs,
    List<ArtifactReference> evidenceRefs,
    List<ArtifactReference> requiredControls,
    List<ArtifactReference> validationArtifactRefs,
    String policyVersion,
    String snapshotDigest,
    String runtimeContextDigest,
    SourcePolicyEvaluationArtifactRef sourcePolicyEvaluationArtifactRef
) {

    public RuntimeDecision {
        runtimeReasonCodes = List.copyOf(runtimeReasonCodes);
        matchedPolicyRefs = List.copyOf(matchedPolicyRefs);
        matchedRuleRefs = List.copyOf(matchedRuleRefs);
        requirementRefs = List.copyOf(requirementRefs);
        evidenceRefs = List.copyOf(evidenceRefs);
        requiredControls = List.copyOf(requiredControls);
        validationArtifactRefs = List.copyOf(validationArtifactRefs);
    }

    public ReasonCode primaryReasonCode() {
        return runtimeReasonCodes().isEmpty() ? ReasonCode.INTERNAL_ERROR : runtimeReasonCodes().getFirst();
    }
}
