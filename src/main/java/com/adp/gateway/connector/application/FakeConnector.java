package com.adp.gateway.connector.application;

import com.adp.gateway.common.contract.RuntimeRequestContext;
import com.adp.gateway.connector.domain.ConnectorResult;
import com.adp.gateway.decision.domain.RuntimeDecision;
import com.adp.gateway.egress.domain.OutboundCandidatePayload;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "adp.mock-runtime.enabled", havingValue = "true")
public class FakeConnector implements RuntimeConnectorPort {

    public ConnectorResult execute(RuntimeRequestContext context, RuntimeDecision decision, OutboundCandidatePayload payload) {
        return new ConnectorResult(
            "fake-connector",
            "EXECUTED",
            payload.outboundPayloadId(),
            payload.payloadDigest()
        );
    }
}
