package com.adp.gateway.policy.api;

import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.NotNull;

public record RollbackPolicyRequest(
    @NotNull @PositiveOrZero Long expectedTargetRevision,
    @NotNull @PositiveOrZero Long expectedSelectionRevision
) {
}
