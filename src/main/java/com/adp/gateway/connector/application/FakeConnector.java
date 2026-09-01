package com.adp.gateway.connector.application;

import java.util.UUID;

import com.adp.gateway.common.contract.RuntimeRequestContext;
import com.adp.gateway.connector.domain.ConnectorResult;
import com.adp.gateway.decision.domain.RuntimeDecision;
import com.adp.gateway.egress.domain.OutboundCandidatePayload;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "adp.mock-runtime.enabled", havingValue = "true")
public class FakeConnector implements RuntimeConnectorPort {

    private final MeterRegistry meterRegistry;

    public FakeConnector(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public ConnectorResult execute(RuntimeRequestContext context, RuntimeDecision decision, OutboundCandidatePayload payload) {
        meterRegistry.counter("connector.execution.total", "status", "EXECUTED").increment();
        return new ConnectorResult(
            "con_" + UUID.randomUUID(),
            "fake-connector",
            "EXECUTED",
            payload.outboundPayloadId(),
            payload.candidatePayloadDigest(),
            "fake-response-digest:" + payload.candidatePayloadDigest(),
            "fake-response-schema-v1"
        );
    }
}
