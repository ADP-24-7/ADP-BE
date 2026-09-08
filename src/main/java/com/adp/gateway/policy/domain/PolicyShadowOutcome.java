package com.adp.gateway.policy.domain;

import java.util.List;
import java.util.Objects;

import com.adp.gateway.decision.domain.FinalAction;

public record PolicyShadowOutcome(
    String evaluationCaseId,
    String evaluationCaseVersion,
    String inputDigest,
    FinalAction finalAction,
    List<String> reasonCodes,
    List<String> requiredControls,
    String transformStrategy,
    String destinationProfileId
) {
    public PolicyShadowOutcome {
        if (evaluationCaseId == null || evaluationCaseId.isBlank()
            || evaluationCaseVersion == null || evaluationCaseVersion.isBlank()
            || inputDigest == null || !inputDigest.matches("[0-9a-f]{64}")
            || transformStrategy == null || transformStrategy.isBlank()
            || destinationProfileId == null || destinationProfileId.isBlank()) {
            throw new IllegalArgumentException("Policy shadow outcome is invalid");
        }
        Objects.requireNonNull(finalAction, "finalAction must not be null");
        Objects.requireNonNull(reasonCodes, "reasonCodes must not be null");
        Objects.requireNonNull(requiredControls, "requiredControls must not be null");
        reasonCodes = reasonCodes.stream().sorted().toList();
        requiredControls = requiredControls.stream().sorted().toList();
    }
}
