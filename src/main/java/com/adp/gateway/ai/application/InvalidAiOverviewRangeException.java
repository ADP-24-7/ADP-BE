package com.adp.gateway.ai.application;

public class InvalidAiOverviewRangeException extends RuntimeException {
    public InvalidAiOverviewRangeException() {
        super("Invalid AI operations overview query");
    }
}
