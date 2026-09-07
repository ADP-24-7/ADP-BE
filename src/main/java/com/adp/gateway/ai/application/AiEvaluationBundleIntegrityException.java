package com.adp.gateway.ai.application;

public class AiEvaluationBundleIntegrityException extends RuntimeException {
    private final String reasonCode;

    public AiEvaluationBundleIntegrityException(String reasonCode) {
        super(reasonCode);
        this.reasonCode = reasonCode;
    }

    public String reasonCode() {
        return reasonCode;
    }
}
