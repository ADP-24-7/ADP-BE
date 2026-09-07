package com.adp.gateway.ai.application;

import com.adp.gateway.context.application.ExecutionPackInputRejectedException;
import com.adp.gateway.egress.domain.ExecutionPackType;

public class AiEvaluationRunMismatchException extends ExecutionPackInputRejectedException {
    public AiEvaluationRunMismatchException(String message) {
        super(ExecutionPackType.AI, message);
    }
}
