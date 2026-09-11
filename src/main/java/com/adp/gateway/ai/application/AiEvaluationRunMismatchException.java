package com.adp.gateway.ai.application;

import java.util.List;

import com.adp.gateway.context.application.ExecutionPackInputRejectedException;
import com.adp.gateway.egress.domain.ExecutionPackType;

public class AiEvaluationRunMismatchException extends ExecutionPackInputRejectedException {
    private final List<String> reasonCodes;

    public AiEvaluationRunMismatchException(String message) {
        super(ExecutionPackType.AI, message);
        this.reasonCodes = List.of(message);
    }

    public AiEvaluationRunMismatchException(List<String> reasonCodes) {
        super(ExecutionPackType.AI, reasonCodes.isEmpty() ? "AI_EVALUATION_CONTRACT_MISMATCH" : reasonCodes.getFirst());
        this.reasonCodes = List.copyOf(reasonCodes);
    }

    public List<String> reasonCodes() {
        return reasonCodes;
    }
}
