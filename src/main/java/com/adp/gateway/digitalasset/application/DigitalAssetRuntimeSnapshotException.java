package com.adp.gateway.digitalasset.application;

public class DigitalAssetRuntimeSnapshotException extends RuntimeException {
    private final String reasonCode;

    public DigitalAssetRuntimeSnapshotException(String reasonCode) {
        super(reasonCode);
        this.reasonCode = reasonCode;
    }

    public DigitalAssetRuntimeSnapshotException(String reasonCode, Throwable cause) {
        super(reasonCode, cause);
        this.reasonCode = reasonCode;
    }

    public String reasonCode() {
        return reasonCode;
    }
}
