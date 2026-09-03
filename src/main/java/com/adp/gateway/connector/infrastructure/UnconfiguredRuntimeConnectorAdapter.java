package com.adp.gateway.connector.infrastructure;

import java.util.UUID;

import com.adp.gateway.common.contract.RuntimeRequestContext;
import com.adp.gateway.connector.application.RuntimeConnectorPort;
import com.adp.gateway.connector.domain.ConnectorResult;
import com.adp.gateway.connector.domain.ConnectorStatus;
import com.adp.gateway.decision.domain.RuntimeDecision;
import com.adp.gateway.egress.domain.OutboundCandidatePayload;
import com.adp.gateway.egress.domain.ProviderRequestPayload;
import com.adp.gateway.egress.domain.ExecutionPackType;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnExpression("'${adp.mock-runtime.enabled:false}' != 'true' && '${adp.ai-connector.enabled:false}' != 'true'")
public class UnconfiguredRuntimeConnectorAdapter implements RuntimeConnectorPort {

    private final MeterRegistry meterRegistry;

    public UnconfiguredRuntimeConnectorAdapter(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public ExecutionPackType supportedPack() {
        return ExecutionPackType.COMMON;
    }

    @Override
    public ConnectorResult execute(
        RuntimeRequestContext context,
        RuntimeDecision decision,
        OutboundCandidatePayload payload,
        ProviderRequestPayload providerRequest
    ) {
        meterRegistry.counter("connector.execution.total", "status", ConnectorStatus.FAILED.name()).increment();
        return new ConnectorResult(
            "con_" + UUID.randomUUID(),
            "unconfigured-runtime-connector",
            ConnectorStatus.FAILED,
            payload.outboundPayloadId(),
            payload.candidatePayloadDigest(),
            null,
            null
        );
    }
}
