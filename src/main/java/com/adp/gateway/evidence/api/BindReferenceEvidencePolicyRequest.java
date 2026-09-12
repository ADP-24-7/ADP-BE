package com.adp.gateway.evidence.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record BindReferenceEvidencePolicyRequest(
    @NotBlank @Size(max = 120) String artifactId,
    @NotBlank @Size(max = 120) String artifactVersion,
    @NotBlank @Pattern(regexp = "^sha256:[0-9a-f]{64}$") String sourceDigest
) {
}
