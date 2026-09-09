package com.adp.gateway.policy.api;

import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.NotNull;

public record ActivatePolicyRequest(
    @NotNull @PositiveOrZero Long expectedArtifactRevision,
    @NotNull @PositiveOrZero Long expectedSelectionRevision
) {
}
