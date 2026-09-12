package com.adp.gateway.evidence.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public record BindReferenceEvidencePolicyRequest(
    @NotBlank @Size(max = 120) String artifactId,
    @NotBlank @Size(max = 120) String artifactVersion,
    @NotBlank @Pattern(regexp = "^sha256:[0-9a-f]{64}$") String sourceDigest,
    @NotEmpty @Size(max = 50) List<@NotBlank @Size(max = 160) String> requirementRefs,
    @NotEmpty @Size(max = 50) List<@NotBlank @Size(max = 160) String> controlRefs
) {
}
