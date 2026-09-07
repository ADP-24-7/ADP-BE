package com.adp.gateway.policy.api;

import com.adp.gateway.policy.domain.PolicyLifecycleStage;
import com.adp.gateway.policy.domain.PolicyLifecycleTransitionReason;
import jakarta.validation.constraints.NotNull;

public record TransitionPolicyLifecycleRequest(
    @NotNull PolicyLifecycleStage targetStage,
    @NotNull PolicyLifecycleTransitionReason reasonCode
) {
}
