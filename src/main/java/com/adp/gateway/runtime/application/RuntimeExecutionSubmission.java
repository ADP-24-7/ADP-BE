package com.adp.gateway.runtime.application;

public record RuntimeExecutionSubmission(
    RuntimeExecutionResult result,
    IdempotentExecutionReplay replay
) {

    public static RuntimeExecutionSubmission created(RuntimeExecutionResult result) {
        return new RuntimeExecutionSubmission(result, null);
    }

    public static RuntimeExecutionSubmission replayed(IdempotentExecutionReplay replay) {
        return new RuntimeExecutionSubmission(null, replay);
    }

    public boolean isReplay() {
        return replay != null;
    }
}
