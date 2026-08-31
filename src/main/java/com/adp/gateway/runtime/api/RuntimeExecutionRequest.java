package com.adp.gateway.runtime.api;

import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RuntimeExecutionRequest(
    @NotBlank @Size(max = 120) String workloadId,
    @NotBlank @Size(max = 160) String purposeCode,
    @NotBlank @Size(max = 240) String subjectScope,
    @NotBlank @Size(max = 120) String providerProfileId,
    @NotBlank @Size(max = 120) String idempotencyKey,
    List<String> processingContexts,
    Map<String, Object> input
) {
}
