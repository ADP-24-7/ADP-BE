package com.adp.gateway.ai.application;

import com.adp.gateway.observability.GatewayObservability;
import com.adp.gateway.observability.GatewayObservability.AiEvaluationEvidenceOutcome;
import com.adp.gateway.runtime.application.RuntimeExecutionPersistence;
import com.adp.gateway.connector.domain.ConnectorResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

@Component
public class AiEvaluationEvidenceRecorder {

    private static final Logger log = LoggerFactory.getLogger(AiEvaluationEvidenceRecorder.class);

    private final RuntimeExecutionPersistence persistence;
    private final GatewayObservability observability;

    public AiEvaluationEvidenceRecorder(
        RuntimeExecutionPersistence persistence,
        GatewayObservability observability
    ) {
        this.persistence = persistence;
        this.observability = observability;
    }

    public void recordInitialRuntimeLatency(String executionId) {
        try {
            AiEvaluationEvidenceOutcome outcome = persistence.recordInitialAiRuntimeLatency(executionId)
                ? AiEvaluationEvidenceOutcome.COMPLETE
                : AiEvaluationEvidenceOutcome.PERSISTENCE_FAILED;
            observability.aiEvaluationEvidence(outcome);
        } catch (DataAccessException exception) {
            observability.aiEvaluationEvidence(AiEvaluationEvidenceOutcome.PERSISTENCE_FAILED);
            log.warn("AI evaluation runtime latency evidence persistence failed");
        }
    }

    public void recordConnectorEvidence(String executionId, ConnectorResult connectorResult) {
        try {
            AiEvaluationEvidenceOutcome outcome = persistence.recordAiConnectorExecutionEvidence(
                executionId, connectorResult
            ) ? AiEvaluationEvidenceOutcome.CONNECTOR_RECORDED : AiEvaluationEvidenceOutcome.PERSISTENCE_FAILED;
            observability.aiEvaluationEvidence(outcome);
        } catch (DataAccessException exception) {
            observability.aiEvaluationEvidence(AiEvaluationEvidenceOutcome.PERSISTENCE_FAILED);
            log.warn("AI connector evaluation evidence persistence failed");
        }
    }
}
