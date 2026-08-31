package com.adp.gateway.policy.application;

import java.util.List;

public record PolicyEvaluationHandoffArtifact(
    String schemaVersion,
    String artifactId,
    String artifactVersion,
    String analysisStatus,
    String policyAction,
    List<HandoffReference> matchedPolicyRefs,
    List<HandoffReference> matchedRuleRefs,
    List<HandoffReference> requirementRefs,
    List<HandoffReference> evidenceRefs,
    List<HandoffReference> requiredControls,
    List<HandoffReference> validationArtifactRefs,
    HandoffApplicability applicability,
    List<String> processingContexts,
    List<String> regulatoryDataCategories,
    HandoffRuntimeBinding runtimeBinding,
    HandoffDigest digest
) {
}
