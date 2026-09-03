package com.adp.gateway.recovery.application;

public class StaleRecoveryLeaseException extends RuntimeException {

    public StaleRecoveryLeaseException() {
        super("Recovery lease is no longer owned by this worker");
    }
}
