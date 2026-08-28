package com.adp.gateway.policy.domain;

import java.util.List;

public record PolicyEvaluation(
    List<ArtifactReference> matchedPolicyRefs,
    List<ArtifactReference> matchedRuleRefs,
    List<ArtifactReference> requirementRefs,
    List<ArtifactReference> evidenceRefs,
    PolicyAction policyAction,
    List<ArtifactReference> requiredControls,
    List<ArtifactReference> validationArtifactRefs
) {

    public PolicyEvaluation {
        matchedPolicyRefs = List.copyOf(matchedPolicyRefs);
        matchedRuleRefs = List.copyOf(matchedRuleRefs);
        requirementRefs = List.copyOf(requirementRefs);
        evidenceRefs = List.copyOf(evidenceRefs);
        requiredControls = List.copyOf(requiredControls);
        validationArtifactRefs = List.copyOf(validationArtifactRefs);
    }
}
