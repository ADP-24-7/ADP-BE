package com.adp.gateway.digitalasset.infrastructure;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.adp.gateway.connector.domain.ConnectorResult;
import com.adp.gateway.connector.domain.ConnectorStatus;
import com.adp.gateway.egress.application.ResponseGuardPort;
import com.adp.gateway.egress.domain.ExecutionPackType;
import com.adp.gateway.egress.domain.OutboundCandidatePayload;
import com.adp.gateway.egress.domain.ResponseGuardResult;
import org.springframework.stereotype.Component;

@Component
public class DigitalAssetResponseGuard implements ResponseGuardPort {
    private static final Set<String> ALLOWED_STATUSES = Set.of(
        "REQUESTED", "POLICY_APPROVED", "READY_TO_SUBMIT", "SUBMITTED", "SETTLING", "SETTLED",
        "FAILED", "SENT_UNKNOWN", "RECONCILIATION_REQUIRED"
    );

    @Override
    public ExecutionPackType supportedPack() {
        return ExecutionPackType.DIGITAL_ASSET;
    }

    @Override
    public ResponseGuardResult guard(OutboundCandidatePayload payload, ConnectorResult result) {
        if (result.status() != ConnectorStatus.ACKNOWLEDGED && result.status() != ConnectorStatus.COMPLETED) {
            return ResponseGuardResult.notEvaluated(List.of("CONNECTOR_NOT_EXECUTED"));
        }
        if (!"digital-asset-settlement/v1".equals(result.responseSchemaVersion())
            || !(result.responsePayload() instanceof Map<?, ?> response)
            || !ALLOWED_STATUSES.contains(response.get("settlementStatus"))
            || !(response.get("externalTransactionId") instanceof String)
            || !(response.get("settlementId") instanceof String)) {
            return ResponseGuardResult.rejected(List.of("SETTLEMENT_RESPONSE_INVALID"));
        }
        return ResponseGuardResult.passed("digital-asset-response-guard/v1");
    }
}
