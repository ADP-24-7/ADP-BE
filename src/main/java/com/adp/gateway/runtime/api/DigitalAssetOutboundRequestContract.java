package com.adp.gateway.runtime.api;

import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "DigitalAssetOutboundRequest")
public record DigitalAssetOutboundRequestContract(
    DigitalAssetDescriptorContract requestedAsset,
    @Schema(description = "Non-negative integer atomic units encoded as a decimal string", example = "10000")
    String requestedAmount,
    @Schema(example = "wallet-test-001") String requestedDestination,
    @Schema(example = "beneficiary-local-001") String requestedBeneficiaryReference,
    Map<String, String> regulatoryOutboundData
) {
}
