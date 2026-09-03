package com.adp.gateway.runtime.application;

public class IdempotencyKeyConflictException extends RuntimeException {

    public IdempotencyKeyConflictException() {
        super("Idempotency key was reused with a different request");
    }
}
