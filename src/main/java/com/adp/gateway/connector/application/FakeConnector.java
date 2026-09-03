package com.adp.gateway.connector.application;

import java.util.UUID;
import java.util.Map;

import com.adp.gateway.common.contract.RuntimeRequestContext;
import com.adp.gateway.connector.domain.ConnectorResult;
import com.adp.gateway.connector.domain.ConnectorStatus;
import com.adp.gateway.decision.domain.RuntimeDecision;
import com.adp.gateway.egress.domain.OutboundCandidatePayload;
import com.adp.gateway.egress.domain.ProviderRequestPayload;
import com.adp.gateway.egress.domain.ExecutionPackType;
import com.adp.gateway.context.application.CanonicalValueHasher;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnExpression("'${adp.mock-runtime.enabled:false}' == 'true' && '${adp.ai-connector.enabled:false}' != 'true'")
public class FakeConnector implements RuntimeConnectorPort {

    private final MeterRegistry meterRegistry;
    private final CanonicalValueHasher hasher;

    public FakeConnector(MeterRegistry meterRegistry, CanonicalValueHasher hasher) {
        this.meterRegistry = meterRegistry;
        this.hasher = hasher;
    }

    @Override
    public ExecutionPackType supportedPack() {
        return ExecutionPackType.COMMON;
    }

    public ConnectorResult execute(
        RuntimeRequestContext context,
        RuntimeDecision decision,
        OutboundCandidatePayload payload,
        ProviderRequestPayload providerRequest
    ) {
        meterRegistry.counter("connector.execution.total", "status", ConnectorStatus.ACKNOWLEDGED.name()).increment();
        Map<String, Object> responsePayload = Map.of(
            "answer", "Approved context processed",
            "providerRequestDigest", providerRequest.canonicalPayloadDigest()
        );
        String responseDigest = hasher.hash(
            "answer=Approved context processed|providerRequestDigest=" + providerRequest.canonicalPayloadDigest()
        );
        return new ConnectorResult(
            "con_" + UUID.randomUUID(),
            "fake-connector",
            ConnectorStatus.ACKNOWLEDGED,
            payload.outboundPayloadId(),
            payload.candidatePayloadDigest(),
            responseDigest,
            "ai-provider-response/v1",
            responsePayload
        );
    }

    public ConnectorResult execute(
        RuntimeRequestContext context,
        RuntimeDecision decision,
        OutboundCandidatePayload payload
    ) {
        ProviderRequestPayload providerRequest = new ProviderRequestPayload(
            "preq_" + UUID.randomUUID(),
            payload.outboundPayloadId(),
            "fake-provider",
            payload.schemaVersion(),
            payload.candidatePayloadDigest(),
            payload.fieldCount(),
            Map.of()
        );
        return execute(context, decision, payload, providerRequest);
    }
}
