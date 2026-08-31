package com.adp.gateway.runtime.api;

import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.NotBlank;

public record RuntimeExecutionRequest(
    @NotBlank String workloadId,
    @NotBlank String purposeCode,
    @NotBlank String subjectScope,
    @NotBlank String providerProfileId,
    @NotBlank String idempotencyKey,
    List<String> processingContexts,
    Map<String, Object> input
) {
}
