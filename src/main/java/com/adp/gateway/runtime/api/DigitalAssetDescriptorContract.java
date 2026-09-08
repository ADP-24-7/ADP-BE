package com.adp.gateway.runtime.api;

import com.adp.gateway.digitalasset.domain.DigitalAssetKind;
import com.adp.gateway.digitalasset.domain.DigitalAssetOperation;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(name = "DigitalAssetDescriptor")
public record DigitalAssetDescriptorContract(
    @NotBlank @Size(max = 80)
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "eip155:1") String chainId,
    @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED) DigitalAssetKind assetKind,
    @NotBlank @Size(max = 64)
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "asset-krw-token-001") String assetSymbol,
    @Size(max = 240)
    @Schema(nullable = true, example = "0x0000000000000000000000000000000000000001") String assetContractAddress,
    @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED) DigitalAssetOperation operation,
    @Size(max = 160)
    @Schema(nullable = true, example = "42") String tokenId
) {
}
