package com.adp.gateway.runtime.application;

public class IdempotencyRequestInProgressException extends RuntimeException {

    public IdempotencyRequestInProgressException() {
        super("Idempotent request is still in progress");
    }
}
