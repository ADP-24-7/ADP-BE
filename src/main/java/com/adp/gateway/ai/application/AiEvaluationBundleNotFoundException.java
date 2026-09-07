package com.adp.gateway.ai.application;

public class AiEvaluationBundleNotFoundException extends RuntimeException {
    public AiEvaluationBundleNotFoundException(String evaluationRunId) {
        super("AI evaluation bundle not found: " + evaluationRunId);
    }
}
