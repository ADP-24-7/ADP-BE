package com.adp.gateway.connector.infrastructure;

import com.adp.gateway.common.contract.RuntimeRequestContext;
import com.adp.gateway.connector.application.RuntimeConnectorPort;
import com.adp.gateway.connector.domain.ConnectorResult;
import com.adp.gateway.decision.domain.RuntimeDecision;
import com.adp.gateway.egress.domain.OutboundCandidatePayload;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "adp.mock-runtime.enabled", havingValue = "false", matchIfMissing = true)
public class UnconfiguredRuntimeConnectorAdapter implements RuntimeConnectorPort {

    @Override
    public ConnectorResult execute(RuntimeRequestContext context, RuntimeDecision decision, OutboundCandidatePayload payload) {
        return new ConnectorResult(
            "unconfigured-runtime-connector",
            "PROVIDER_NOT_CONFIGURED",
            payload.outboundPayloadId(),
            payload.payloadDigest()
        );
    }
}
