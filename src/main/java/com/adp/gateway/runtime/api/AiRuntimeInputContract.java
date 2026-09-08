package com.adp.gateway.runtime.api;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "AiRuntimeInput", description = "AI execution pack input")
public record AiRuntimeInputContract(
    @Schema(example = "Summarize the approved customer context") String prompt
) {
}
