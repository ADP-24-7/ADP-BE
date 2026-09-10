package com.adp.gateway.digitalasset.domain;

import java.util.List;

public record DigitalAssetArtifactCurrentStatePage(
    List<DigitalAssetArtifactCurrentStateItem> items,
    int page,
    int size,
    long totalElements
) {
    public DigitalAssetArtifactCurrentStatePage {
        items = List.copyOf(items);
    }
}
