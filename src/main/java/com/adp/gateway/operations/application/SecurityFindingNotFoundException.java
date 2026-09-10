package com.adp.gateway.operations.application;

public class SecurityFindingNotFoundException extends RuntimeException {
    public SecurityFindingNotFoundException(long findingId) {
        super("Security finding not found: " + findingId);
    }
}
