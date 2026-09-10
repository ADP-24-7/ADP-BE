package com.adp.gateway.auth.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
    @NotBlank @Size(max = 80) String principalId,
    @NotBlank @Size(max = 200) String password
) {
}
