package com.adp.gateway.common.error;

public class InvalidRuntimeHeaderException extends RuntimeException {

    public InvalidRuntimeHeaderException(String headerName) {
        super("Invalid runtime header: " + headerName);
    }
}
