package com.adp.gateway.operations.api;

import jakarta.validation.constraints.NotBlank;

public record MockRuntimeRequest(
    @NotBlank String workloadId,
    @NotBlank String purpose,
    @NotBlank String subject
) {
}
