package com.adp.gateway.ai.application;

public class AiInputRejectedException extends RuntimeException {

    private final String reasonCode;

    public AiInputRejectedException(String reasonCode) {
        super("AI input did not satisfy the workload contract");
        this.reasonCode = reasonCode;
    }

    public String reasonCode() {
        return reasonCode;
    }
}
