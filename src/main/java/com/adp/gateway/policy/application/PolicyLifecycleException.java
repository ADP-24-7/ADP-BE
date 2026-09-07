package com.adp.gateway.policy.application;

public class PolicyLifecycleException extends RuntimeException {
    private final String reasonCode;

    public PolicyLifecycleException(String reasonCode) {
        super("Policy lifecycle operation rejected");
        this.reasonCode = reasonCode;
    }

    public String reasonCode() {
        return reasonCode;
    }
}
