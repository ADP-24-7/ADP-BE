package com.adp.gateway.recovery.application;

public class ExternalStatusQueryUnavailableException extends RuntimeException {

    public ExternalStatusQueryUnavailableException() {
        super("External status query adapter is not configured");
    }
}
