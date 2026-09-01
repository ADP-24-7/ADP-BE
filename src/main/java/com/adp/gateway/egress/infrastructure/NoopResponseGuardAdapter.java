package com.adp.gateway.egress.infrastructure;

import com.adp.gateway.connector.domain.ConnectorResult;
import com.adp.gateway.egress.application.ResponseGuardPort;
import com.adp.gateway.egress.domain.OutboundCandidatePayload;
import com.adp.gateway.egress.domain.ResponseGuardResult;
import org.springframework.stereotype.Component;

@Component
public class NoopResponseGuardAdapter implements ResponseGuardPort {

    @Override
    public ResponseGuardResult guard(OutboundCandidatePayload payload, ConnectorResult connectorResult) {
        return ResponseGuardResult.passed();
    }
}
