package com.adp.gateway.policy.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RunPolicyShadowEvaluationRequest(
    @NotBlank @Size(max = 120) String evaluationCaseId
) {
}
