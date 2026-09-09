package com.adp.gateway.recovery.application;

public class ExternalRetryUnavailableException extends RuntimeException {

    public ExternalRetryUnavailableException() {
        super("External retry adapter is not configured");
    }
}
