package com.adp.gateway.policy.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ApprovePolicyLifecycleRequest(
    @NotBlank @Size(max = 80) String shadowEvaluationId
) {
}
