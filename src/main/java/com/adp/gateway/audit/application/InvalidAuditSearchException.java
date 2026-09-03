package com.adp.gateway.audit.application;

public class InvalidAuditSearchException extends RuntimeException {
    public InvalidAuditSearchException(String message) {
        super(message);
    }
}
