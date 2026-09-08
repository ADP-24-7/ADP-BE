package com.adp.gateway.digitalasset.application;

public interface DigitalAssetArtifactContentStore {
    String load(String reference, long maxBytes);
}
