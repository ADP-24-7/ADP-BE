package com.adp.gateway.egress.infrastructure;

import java.util.List;

import com.adp.gateway.connector.domain.ConnectorResult;
import com.adp.gateway.egress.application.ResponseGuardPort;
import com.adp.gateway.egress.domain.OutboundCandidatePayload;
import com.adp.gateway.egress.domain.ResponseGuardResult;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "adp.local-fixtures.enabled", havingValue = "false", matchIfMissing = true)
public class NoopResponseGuardAdapter implements ResponseGuardPort {

    private final MeterRegistry meterRegistry;

    public NoopResponseGuardAdapter(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public ResponseGuardResult guard(OutboundCandidatePayload payload, ConnectorResult connectorResult) {
        if (!"EXECUTED".equals(connectorResult.status())) {
            return record(ResponseGuardResult.notEvaluated(List.of("CONNECTOR_NOT_EXECUTED")));
        }
        return record(ResponseGuardResult.notEvaluated(List.of("RESPONSE_GUARD_NOT_CONFIGURED")));
    }

    private ResponseGuardResult record(ResponseGuardResult result) {
        result.reasonCodes().forEach(reasonCode -> meterRegistry.counter(
            "response.guard.total",
            "result", result.status(),
            "reason", reasonCode
        ).increment());
        return result;
    }
}
