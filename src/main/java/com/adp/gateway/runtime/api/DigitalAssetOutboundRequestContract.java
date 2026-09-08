package com.adp.gateway.runtime.api;

import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(name = "DigitalAssetOutboundRequest")
public record DigitalAssetOutboundRequestContract(
    @Valid @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    DigitalAssetDescriptorContract requestedAsset,
    @NotBlank @Pattern(regexp = "(?!0+$)[0-9]{1,78}")
    @Schema(
        requiredMode = Schema.RequiredMode.REQUIRED,
        description = "Positive integer atomic units encoded as a decimal string",
        pattern = "(?!0+$)[0-9]{1,78}", example = "10000"
    )
    String requestedAmount,
    @NotBlank @Size(max = 240)
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "wallet-test-001")
    String requestedDestination,
    @NotBlank @Size(max = 240)
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "beneficiary-local-001")
    String requestedBeneficiaryReference,
    @NotNull @Size(max = 0)
    @Schema(
        requiredMode = Schema.RequiredMode.REQUIRED,
        description = "Reserved for the P0-4 allowlist; must currently be empty",
        additionalProperties = Schema.AdditionalPropertiesValue.FALSE
    )
    Map<String, String> regulatoryOutboundData
) {
}
