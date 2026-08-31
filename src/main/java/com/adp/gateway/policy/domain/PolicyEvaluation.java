package com.adp.gateway.policy.domain;

import java.util.List;
import java.util.Objects;

public record PolicyEvaluation(
    List<ArtifactReference> matchedPolicyRefs,
    List<ArtifactReference> matchedRuleRefs,
    List<ArtifactReference> requirementRefs,
    List<ArtifactReference> evidenceRefs,
    PolicyAction policyAction,
    List<ArtifactReference> requiredControls,
    List<ArtifactReference> validationArtifactRefs,
    PolicyApplicabilitySpec applicabilitySpec
) {

    public PolicyEvaluation {
        Objects.requireNonNull(policyAction, "policyAction must not be null");
        Objects.requireNonNull(applicabilitySpec, "applicabilitySpec must not be null");
        matchedPolicyRefs = List.copyOf(matchedPolicyRefs);
        matchedRuleRefs = List.copyOf(matchedRuleRefs);
        requirementRefs = List.copyOf(requirementRefs);
        evidenceRefs = List.copyOf(evidenceRefs);
        requiredControls = List.copyOf(requiredControls);
        validationArtifactRefs = List.copyOf(validationArtifactRefs);
    }
}
