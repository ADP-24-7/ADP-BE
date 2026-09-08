package com.adp.gateway.digitalasset.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class DigitalAssetDescriptorTests {

    @Test
    void acceptsNativeAssetWithConditionalFieldsOmitted() {
        DigitalAssetDescriptor descriptor = DigitalAssetDescriptor.from(asset("NATIVE"));

        assertThat(descriptor.assetContractAddress()).isNull();
        assertThat(descriptor.tokenId()).isNull();
    }

    @Test
    void acceptsFungibleTokenWithContractAddressAndTokenIdOmitted() {
        Map<String, Object> asset = asset("FUNGIBLE_TOKEN");
        asset.put("assetContractAddress", "0x0000000000000000000000000000000000000001");

        DigitalAssetDescriptor descriptor = DigitalAssetDescriptor.from(asset);

        assertThat(descriptor.assetContractAddress()).isNotNull();
        assertThat(descriptor.tokenId()).isNull();
    }

    @Test
    void rejectsFungibleTokenWithoutContractAddress() {
        assertThatThrownBy(() -> DigitalAssetDescriptor.from(asset("FUNGIBLE_TOKEN")))
            .hasMessage("DIGITAL_ASSET_ASSET_INVALID");
    }

    @Test
    void acceptsNonFungibleTokenWithContractAddressAndTokenId() {
        Map<String, Object> asset = asset("NON_FUNGIBLE_TOKEN");
        asset.put("assetContractAddress", "0x0000000000000000000000000000000000000001");
        asset.put("tokenId", "42");

        DigitalAssetDescriptor descriptor = DigitalAssetDescriptor.from(asset);

        assertThat(descriptor.tokenId()).isEqualTo("42");
    }

    @Test
    void rejectsNonFungibleTokenWithoutTokenId() {
        Map<String, Object> asset = asset("NON_FUNGIBLE_TOKEN");
        asset.put("assetContractAddress", "0x0000000000000000000000000000000000000001");

        assertThatThrownBy(() -> DigitalAssetDescriptor.from(asset))
            .hasMessage("DIGITAL_ASSET_ASSET_INVALID");
    }

    @Test
    void rejectsUnknownAssetField() {
        Map<String, Object> asset = asset("NATIVE");
        asset.put("network", "ethereum");

        assertThatThrownBy(() -> DigitalAssetDescriptor.from(asset))
            .hasMessage("DIGITAL_ASSET_ASSET_SCHEMA_MISMATCH");
    }

    private Map<String, Object> asset(String kind) {
        Map<String, Object> asset = new HashMap<>();
        asset.put("chainId", "eip155:1");
        asset.put("assetKind", kind);
        asset.put("assetSymbol", "ASSET");
        asset.put("operation", "TRANSFER");
        return asset;
    }
}
