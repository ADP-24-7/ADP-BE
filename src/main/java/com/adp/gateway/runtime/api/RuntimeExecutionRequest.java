package com.adp.gateway.runtime.api;

import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RuntimeExecutionRequest(
    @NotBlank @Size(max = 120) String institutionId,
    @NotBlank @Size(max = 160) String approvalReference,
    @NotBlank @Size(max = 120) String workloadId,
    @NotBlank @Size(max = 160) String purposeCode,
    @NotBlank @Size(max = 240) String subjectScope,
    @NotBlank @Size(max = 120) String destinationProfileId,
    @NotBlank @Size(max = 120) String idempotencyKey,
    @Size(max = 10) List<@NotBlank @Size(max = 80) String> processingContexts,
    Map<String, Object> input
) {
}
