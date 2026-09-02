package com.adp.gateway.runtime.application;

import java.util.List;
import java.util.Map;

import com.adp.gateway.connector.domain.ConnectorResult;
import com.adp.gateway.egress.domain.ResponseGuardResult;
import com.adp.gateway.runtime.domain.ControlledDeliveryResult;
import org.springframework.stereotype.Service;

@Service
public class ControlledDeliveryService {

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
