package com.adp.gateway.operations.application;

public class AdminIdentityNotFoundException extends RuntimeException {
    public AdminIdentityNotFoundException(String principalId) {
        super("Admin identity not found: " + principalId);
    }
}
