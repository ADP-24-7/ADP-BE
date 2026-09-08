package com.adp.gateway.digitalasset.infrastructure;

import com.adp.gateway.digitalasset.application.DigitalAssetArtifactContentStore;
import com.adp.gateway.digitalasset.application.DigitalAssetArtifactIngestionException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    name = "adp.digital-asset.artifact-store.type",
    havingValue = "disabled",
    matchIfMissing = true
)
public class UnconfiguredDigitalAssetArtifactContentStore implements DigitalAssetArtifactContentStore {
    @Override
    public String load(String reference, long maxBytes) {
        throw new DigitalAssetArtifactIngestionException("DIGITAL_ASSET_ARTIFACT_STORE_UNAVAILABLE");
    }
}
