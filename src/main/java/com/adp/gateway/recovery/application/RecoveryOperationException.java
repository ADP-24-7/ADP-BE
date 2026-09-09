package com.adp.gateway.recovery.application;

public class RecoveryOperationException extends RuntimeException {

    private final String reasonCode;

    public RecoveryOperationException(String reasonCode) {
        super(reasonCode);
        this.reasonCode = reasonCode;
    }

    public String reasonCode() {
        return reasonCode;
    }
}
