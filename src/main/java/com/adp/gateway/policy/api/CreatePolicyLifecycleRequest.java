package com.adp.gateway.policy.api;

import com.adp.gateway.egress.domain.ExecutionPackType;
import com.adp.gateway.policy.domain.PolicyLayer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreatePolicyLifecycleRequest(
    @NotBlank @Size(max = 120) String artifactId,
    @NotBlank @Size(max = 120) String artifactVersion,
    @NotBlank @Pattern(regexp = "[0-9a-f]{64}") String artifactDigest,
    @NotNull PolicyLayer policyLayer,
    @NotNull ExecutionPackType executionPack,
    @NotBlank @Size(max = 120) String workloadId,
    @NotBlank @Size(max = 120) String purposeCode
) {
}
