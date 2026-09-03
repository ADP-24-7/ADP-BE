package com.adp.gateway.runtime.application;

import java.util.List;
import java.util.Map;

import com.adp.gateway.connector.domain.ConnectorResult;
import com.adp.gateway.egress.domain.ResponseGuardResult;
import com.adp.gateway.egress.domain.ExecutionPackType;
import com.adp.gateway.egress.domain.ProviderRequestPayload;
import com.adp.gateway.runtime.domain.ControlledDeliveryResult;
import com.adp.gateway.runtime.domain.RuntimeExecutionStatus;
import org.springframework.stereotype.Service;

@Service
public class ControlledDeliveryService {

    private final Map<ExecutionPackType, ExecutionPackOutcomeHandler> outcomeHandlers;

    public ControlledDeliveryService(List<ExecutionPackOutcomeHandler> outcomeHandlers) {
        this.outcomeHandlers = outcomeHandlers.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
            ExecutionPackOutcomeHandler::supportedPack,
            handler -> handler
        ));
    }

    public ExecutionPackOutcome resolve(
        ExecutionPackType packType,
        String executionId,
        ProviderRequestPayload providerRequest,
        ConnectorResult connectorResult,
        ResponseGuardResult responseGuardResult
    ) {
        ExecutionPackOutcomeHandler handler = outcomeHandlers.get(packType);
        if (handler != null) {
            return handler.resolve(executionId, providerRequest, connectorResult, responseGuardResult);
        }
        ControlledDeliveryResult delivery = deliver(connectorResult, responseGuardResult);
        RuntimeExecutionStatus status;
        if (connectorResult.status() == com.adp.gateway.connector.domain.ConnectorStatus.FAILED) {
            status = RuntimeExecutionStatus.FAILED;
        } else if (connectorResult.status() == com.adp.gateway.connector.domain.ConnectorStatus.SENT_UNKNOWN) {
            status = RuntimeExecutionStatus.EGRESSING;
        } else {
            status = responseGuardResult.isPassed() && delivery.isDelivered()
                ? RuntimeExecutionStatus.COMPLETED
                : RuntimeExecutionStatus.BLOCKED;
        }
        return new ExecutionPackOutcome(status, delivery);
    }

    public ControlledDeliveryResult deliver(
        ConnectorResult connectorResult,
        ResponseGuardResult responseGuardResult
    ) {
        if (!responseGuardResult.isPassed()) {
            return ControlledDeliveryResult.withheld(
                connectorResult.responseDigest(),
                "RESPONSE_GUARD_" + responseGuardResult.status()
            );
        }
        String content = extractContent(connectorResult.responsePayload());
        if (content == null || content.isBlank()) {
            return ControlledDeliveryResult.withheld(connectorResult.responseDigest(), "UNSUPPORTED_RESPONSE_SCHEMA");
        }
        return ControlledDeliveryResult.delivered(content, connectorResult.responseDigest());
    }

    private String extractContent(Object payload) {
        if (!(payload instanceof Map<?, ?> response)) {
            return null;
        }
        Object answer = response.get("answer");
        if (answer instanceof String value) {
            return value;
        }
        Object choices = response.get("choices");
        if (!(choices instanceof List<?> list) || list.isEmpty() || !(list.getFirst() instanceof Map<?, ?> choice)) {
            return null;
        }
        Object message = choice.get("message");
        if (!(message instanceof Map<?, ?> messageMap)) {
            return null;
        }
        Object content = messageMap.get("content");
        return content instanceof String value ? value : null;
    }
}
