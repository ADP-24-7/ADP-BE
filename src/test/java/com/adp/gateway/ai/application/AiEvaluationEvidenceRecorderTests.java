package com.adp.gateway.ai.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.adp.gateway.observability.GatewayObservability;
import com.adp.gateway.observability.GatewayObservability.AiEvaluationEvidenceOutcome;
import com.adp.gateway.runtime.application.RuntimeExecutionPersistence;
import com.adp.gateway.connector.domain.ConnectorResult;
import com.adp.gateway.connector.domain.ConnectorStatus;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

class AiEvaluationEvidenceRecorderTests {

    @Test
    void observesPersistenceFailureWithoutPropagatingItToCompletedRuntime() {
        RuntimeExecutionPersistence persistence = mock(RuntimeExecutionPersistence.class);
        GatewayObservability observability = mock(GatewayObservability.class);
        var recorder = new AiEvaluationEvidenceRecorder(persistence, observability);
        org.mockito.Mockito.doThrow(new DataAccessResourceFailureException("database unavailable"))
            .when(persistence).recordInitialAiRuntimeLatency("exec_completed");

        assertThatCode(() -> recorder.recordInitialRuntimeLatency("exec_completed"))
            .doesNotThrowAnyException();

        verify(observability).aiEvaluationEvidence(AiEvaluationEvidenceOutcome.PERSISTENCE_FAILED);
    }

    @Test
    void observesCompleteEvidencePersistence() {
        RuntimeExecutionPersistence persistence = mock(RuntimeExecutionPersistence.class);
        GatewayObservability observability = mock(GatewayObservability.class);
        var recorder = new AiEvaluationEvidenceRecorder(persistence, observability);
        org.mockito.Mockito.when(persistence.recordInitialAiRuntimeLatency("exec_completed")).thenReturn(true);

        recorder.recordInitialRuntimeLatency("exec_completed");

        verify(persistence).recordInitialAiRuntimeLatency("exec_completed");
        verify(observability).aiEvaluationEvidence(AiEvaluationEvidenceOutcome.COMPLETE);
    }

    @Test
    void connectorEvidenceFailureDoesNotPropagateToRuntime() {
        RuntimeExecutionPersistence persistence = mock(RuntimeExecutionPersistence.class);
        GatewayObservability observability = mock(GatewayObservability.class);
        var recorder = new AiEvaluationEvidenceRecorder(persistence, observability);
        ConnectorResult connectorResult = new ConnectorResult("connector", ConnectorStatus.ACKNOWLEDGED);
        org.mockito.Mockito.doThrow(new DataAccessResourceFailureException("database unavailable"))
            .when(persistence).recordAiConnectorExecutionEvidence("exec_completed", connectorResult);

        assertThatCode(() -> recorder.recordConnectorEvidence("exec_completed", connectorResult))
            .doesNotThrowAnyException();

        verify(observability).aiEvaluationEvidence(AiEvaluationEvidenceOutcome.PERSISTENCE_FAILED);
    }
}
