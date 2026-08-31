package com.adp.gateway.runtime.application;

public class RuntimeExecutionNotFoundException extends RuntimeException {

    public RuntimeExecutionNotFoundException(String executionId) {
        super("Runtime execution not found: " + executionId);
    }
}
