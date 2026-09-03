package com.adp.gateway.runtime.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import com.adp.gateway.connector.domain.ConnectorResult;
import com.adp.gateway.connector.domain.ConnectorStatus;
import com.adp.gateway.egress.domain.ResponseGuardResult;
import org.junit.jupiter.api.Test;

class ControlledDeliveryServiceTests {

    private final ControlledDeliveryService service = new ControlledDeliveryService(List.of());

    @Test
    void deliversOnlyContentFromAResponseThatPassedTheGuard() {
        var result = service.deliver(connector(Map.of(
            "choices", List.of(Map.of("message", Map.of("content", "safe answer")))
        )), ResponseGuardResult.passed("detector-v1"));

        assertThat(result.deliveryStatus()).isEqualTo("DELIVERED");
        assertThat(result.content()).isEqualTo("safe answer");
        assertThat(result.responseDigest()).isEqualTo("response-digest");
    }

    @Test
    void withholdsContentWhenTheResponseGuardRejectsIt() {
        var result = service.deliver(
            connector(Map.of("answer", "sensitive answer")),
            ResponseGuardResult.rejected(List.of("SENSITIVE_RESPONSE"))
        );

        assertThat(result.deliveryStatus()).isEqualTo("WITHHELD");
        assertThat(result.content()).isNull();
    }

    @Test
    void withholdsAnUnknownResponseSchemaEvenWhenTheGuardPasses() {
        var result = service.deliver(
            connector(Map.of("unexpected", "value")),
            ResponseGuardResult.passed("detector-v1")
        );

        assertThat(result.isDelivered()).isFalse();
        assertThat(result.content()).isNull();
    }

    private ConnectorResult connector(Object payload) {
        return new ConnectorResult(
            "connector-execution",
            "connector",
            ConnectorStatus.ACKNOWLEDGED,
            "outbound",
            "outbound-digest",
            "response-digest",
            "response-v1",
            payload
        );
    }
}
