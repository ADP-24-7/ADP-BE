package com.adp.gateway.egress.infrastructure;

import java.util.List;

import com.adp.gateway.connector.domain.ConnectorResult;
import com.adp.gateway.connector.domain.ConnectorStatus;
import com.adp.gateway.egress.application.ResponseGuardPort;
import com.adp.gateway.egress.application.ResponseLeakageDetector;
import com.adp.gateway.egress.domain.OutboundCandidatePayload;
import com.adp.gateway.egress.domain.ExecutionPackType;
import com.adp.gateway.egress.domain.ResponseGuardResult;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "adp.local-fixtures.enabled", havingValue = "true")
public class ProjectProvisionalResponseGuardAdapter implements ResponseGuardPort {

    private static final String EXPECTED_RESPONSE_SCHEMA_VERSION = "ai-provider-response/v1";

    private final MeterRegistry meterRegistry;
    private final ResponseLeakageDetector leakageDetector;

    public ProjectProvisionalResponseGuardAdapter(
        MeterRegistry meterRegistry,
        ResponseLeakageDetector leakageDetector
    ) {
        this.meterRegistry = meterRegistry;
        this.leakageDetector = leakageDetector;
    }

    @Override
    public ExecutionPackType supportedPack() {
        return ExecutionPackType.AI;
    }

    @Override
    public ResponseGuardResult guard(OutboundCandidatePayload payload, ConnectorResult connectorResult) {
        if (connectorResult.status() != ConnectorStatus.ACKNOWLEDGED
            && connectorResult.status() != ConnectorStatus.COMPLETED) {
            return record(ResponseGuardResult.notEvaluated(List.of("CONNECTOR_NOT_EXECUTED")));
        }
        if (connectorResult.responseDigest() == null || connectorResult.responseSchemaVersion() == null) {
            return record(ResponseGuardResult.notEvaluated(List.of("RESPONSE_METADATA_MISSING")));
        }
        if (!EXPECTED_RESPONSE_SCHEMA_VERSION.equals(connectorResult.responseSchemaVersion())) {
            return record(ResponseGuardResult.rejected(List.of("RESPONSE_SCHEMA_VERSION_MISMATCH")));
        }
        if (connectorResult.responsePayload() == null) {
            return record(ResponseGuardResult.notEvaluated(List.of("RESPONSE_PAYLOAD_MISSING")));
        }
        var findings = leakageDetector.detect(payload, connectorResult.responsePayload());
        if (!findings.isEmpty()) {
            return record(ResponseGuardResult.rejected(
                List.of("RESPONSE_SENSITIVE_DATA_DETECTED"),
                leakageDetector.detectorVersion(),
                findings
            ));
        }
        return record(ResponseGuardResult.passed(leakageDetector.detectorVersion()));
    }

    private ResponseGuardResult record(ResponseGuardResult result) {
        List<String> reasons = result.reasonCodes().isEmpty() ? List.of("NONE") : result.reasonCodes();
        reasons.forEach(reasonCode -> meterRegistry.counter(
            "response.guard.total",
            "result", result.status(),
            "reason", reasonCode
        ).increment());
        return result;
    }
}
