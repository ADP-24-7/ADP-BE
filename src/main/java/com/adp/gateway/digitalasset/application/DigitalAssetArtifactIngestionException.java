package com.adp.gateway.digitalasset.application;

public class DigitalAssetArtifactIngestionException extends RuntimeException {
    private final String reasonCode;

    public DigitalAssetArtifactIngestionException(String reasonCode) {
        super(reasonCode);
        this.reasonCode = reasonCode;
    }

    public DigitalAssetArtifactIngestionException(String reasonCode, Throwable cause) {
        super(reasonCode, cause);
        this.reasonCode = reasonCode;
    }

    public String reasonCode() {
        return reasonCode;
    }
}
