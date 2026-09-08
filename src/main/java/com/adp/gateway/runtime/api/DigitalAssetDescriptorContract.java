package com.adp.gateway.runtime.api;

import com.adp.gateway.digitalasset.domain.DigitalAssetKind;
import com.adp.gateway.digitalasset.domain.DigitalAssetOperation;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "DigitalAssetDescriptor")
public record DigitalAssetDescriptorContract(
    @Schema(example = "eip155:1") String chainId,
    DigitalAssetKind assetKind,
    @Schema(example = "asset-krw-token-001") String assetSymbol,
    @Schema(nullable = true, example = "0x0000000000000000000000000000000000000001") String assetContractAddress,
    DigitalAssetOperation operation,
    @Schema(nullable = true, example = "42") String tokenId
) {
}
