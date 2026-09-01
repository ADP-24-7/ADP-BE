package com.adp.gateway.connector.infrastructure;

import java.util.UUID;

import com.adp.gateway.common.contract.RuntimeRequestContext;
import com.adp.gateway.connector.application.RuntimeConnectorPort;
import com.adp.gateway.connector.domain.ConnectorResult;
import com.adp.gateway.decision.domain.RuntimeDecision;
import com.adp.gateway.egress.domain.OutboundCandidatePayload;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "adp.mock-runtime.enabled", havingValue = "false", matchIfMissing = true)
public class UnconfiguredRuntimeConnectorAdapter implements RuntimeConnectorPort {

    private final MeterRegistry meterRegistry;

    public UnconfiguredRuntimeConnectorAdapter(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public ConnectorResult execute(RuntimeRequestContext context, RuntimeDecision decision, OutboundCandidatePayload payload) {
        meterRegistry.counter("connector.execution.total", "status", "PROVIDER_NOT_CONFIGURED").increment();
        return new ConnectorResult(
            "con_" + UUID.randomUUID(),
            "unconfigured-runtime-connector",
            "PROVIDER_NOT_CONFIGURED",
            payload.outboundPayloadId(),
            payload.candidatePayloadDigest(),
            null,
            null
        );
    }
}
