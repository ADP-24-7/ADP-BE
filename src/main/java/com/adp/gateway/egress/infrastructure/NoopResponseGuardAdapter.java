package com.adp.gateway.egress.infrastructure;

import java.util.List;

import com.adp.gateway.connector.domain.ConnectorResult;
import com.adp.gateway.egress.application.ResponseGuardPort;
import com.adp.gateway.egress.domain.OutboundCandidatePayload;
import com.adp.gateway.egress.domain.ResponseGuardResult;
import org.springframework.stereotype.Component;

@Component
public class NoopResponseGuardAdapter implements ResponseGuardPort {

    @Override
    public ResponseGuardResult guard(OutboundCandidatePayload payload, ConnectorResult connectorResult) {
        if (!"EXECUTED".equals(connectorResult.status())) {
            return ResponseGuardResult.notEvaluated(List.of("CONNECTOR_NOT_EXECUTED"));
        }
        if (connectorResult.responseDigest() == null || connectorResult.responseSchemaVersion() == null) {
            return ResponseGuardResult.notEvaluated(List.of("RESPONSE_METADATA_MISSING"));
        }
        return ResponseGuardResult.passed();
    }
}
