package com.adp.gateway.digitalasset.infrastructure;

import java.util.List;
import java.util.Map;

import com.adp.gateway.connector.domain.ConnectorResult;
import com.adp.gateway.connector.domain.ConnectorStatus;
import com.adp.gateway.egress.application.ResponseGuardPort;
import com.adp.gateway.egress.domain.ExecutionPackType;
import com.adp.gateway.egress.domain.OutboundCandidatePayload;
import com.adp.gateway.egress.domain.ResponseGuardResult;
import com.adp.gateway.digitalasset.domain.DigitalAssetCanonicalContract;
import com.adp.gateway.digitalasset.domain.ExternalExecutionResult;
import org.springframework.stereotype.Component;

@Component
public class DigitalAssetResponseGuard implements ResponseGuardPort {
    @Override
    public ExecutionPackType supportedPack() {
        return ExecutionPackType.DIGITAL_ASSET;
    }

    @Override
    public ResponseGuardResult guard(OutboundCandidatePayload payload, ConnectorResult result) {
        if (result.status() != ConnectorStatus.ACKNOWLEDGED && result.status() != ConnectorStatus.COMPLETED) {
            return ResponseGuardResult.notEvaluated(List.of("CONNECTOR_NOT_EXECUTED"));
        }
        if (!DigitalAssetCanonicalContract.EXTERNAL_RESULT_SCHEMA_VERSION.equals(result.responseSchemaVersion())
            || !(result.responsePayload() instanceof Map<?, ?> response)) {
            return ResponseGuardResult.rejected(List.of("SETTLEMENT_RESPONSE_INVALID"));
        }
        try {
            ExternalExecutionResult.from(response, result.responseDigest());
        } catch (IllegalArgumentException exception) {
            return ResponseGuardResult.rejected(List.of("SETTLEMENT_RESPONSE_INVALID"));
        }
        return ResponseGuardResult.passed("digital-asset-external-result-guard/v1");
    }
}
