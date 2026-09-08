package com.adp.gateway.runtime.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(name = "DigitalAssetRuntimeInput", description = "DA-P0-3 caller-controlled Digital Asset input")
public record DigitalAssetRuntimeInputContract(
    @NotBlank @Size(max = 160)
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "approved-tx-local-001")
    String approvedTransactionReference,
    @NotBlank @Size(max = 240)
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "customer-100") String customerId,
    @NotBlank @Size(max = 240)
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "acct-100-1") String accountId,
    @Valid @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    DigitalAssetOutboundRequestContract outboundRequest
) {
}
