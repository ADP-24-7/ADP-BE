package com.adp.gateway.evidence.application;

public class ReferenceEvidenceException extends RuntimeException {
    private final String reasonCode;

    public ReferenceEvidenceException(String reasonCode) {
        super(reasonCode);
        this.reasonCode = reasonCode;
    }

    public ReferenceEvidenceException(String reasonCode, Throwable cause) {
        super(reasonCode, cause);
        this.reasonCode = reasonCode;
    }

    public String reasonCode() {
        return reasonCode;
    }
}
