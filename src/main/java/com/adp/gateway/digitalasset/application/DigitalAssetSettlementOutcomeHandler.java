package com.adp.gateway.digitalasset.application;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import com.adp.gateway.connector.domain.ConnectorResult;
import com.adp.gateway.connector.domain.ConnectorStatus;
import com.adp.gateway.egress.domain.ExecutionPackType;
import com.adp.gateway.egress.domain.ProviderRequestPayload;
import com.adp.gateway.egress.domain.ResponseGuardResult;
import com.adp.gateway.runtime.application.ExecutionPackOutcome;
import com.adp.gateway.runtime.application.ExecutionPackOutcomeHandler;
import com.adp.gateway.runtime.domain.ControlledDeliveryResult;
import com.adp.gateway.runtime.domain.RuntimeExecutionStatus;
import org.springframework.stereotype.Component;

@Component
public class DigitalAssetSettlementOutcomeHandler implements ExecutionPackOutcomeHandler {
    private static final Set<String> CRITICAL_FIELDS = Set.of("walletAddress", "assetId", "amount");
    private final DigitalAssetTransactionPersistencePort persistence;

    public DigitalAssetSettlementOutcomeHandler(DigitalAssetTransactionPersistencePort persistence) {
        this.persistence = persistence;
    }

    @Override
    public ExecutionPackType supportedPack() {
        return ExecutionPackType.DIGITAL_ASSET;
    }

    @Override
    public ExecutionPackOutcome resolve(String executionId, ProviderRequestPayload request,
                                        ConnectorResult connector, ResponseGuardResult guard) {
        if (connector.status() == ConnectorStatus.SENT_UNKNOWN) {
            persistence.record(executionId, request.providerCorrelationKey(), null, null,
                "SENT_UNKNOWN", "WAIT", null);
            return outcome(RuntimeExecutionStatus.EGRESSING, connector, "SETTLEMENT_UNKNOWN");
        }
        if (connector.status() == ConnectorStatus.FAILED) {
            persistence.record(executionId, request.providerCorrelationKey(), null, null,
                "FAILED", "WAIT", connector.responseDigest());
            return outcome(RuntimeExecutionStatus.FAILED, connector, "ASSET_PLATFORM_FAILED");
        }
        if (!guard.isPassed() || !(connector.responsePayload() instanceof Map<?, ?> response)) {
            return outcome(RuntimeExecutionStatus.BLOCKED, connector, "SETTLEMENT_RESPONSE_REJECTED");
        }

        String responseRequestId = string(response.get("externalRequestId"));
        if (!request.providerCorrelationKey().equals(responseRequestId)) {
            return outcome(RuntimeExecutionStatus.REVIEW_REQUIRED, connector, "EXTERNAL_REQUEST_CORRELATION_MISMATCH");
        }

        String settlementStatus = String.valueOf(response.get("settlementStatus"));
        String reconciliation = reconciliation(request.payload(), response, settlementStatus);
        String externalTransactionId = string(response.get("externalTransactionId"));
        String settlementId = string(response.get("settlementId"));
        persistence.record(executionId, request.providerCorrelationKey(), externalTransactionId, settlementId,
            settlementStatus, reconciliation, connector.responseDigest());

        if (("MATCH".equals(reconciliation) || "RECOVERED".equals(reconciliation))
            && "SETTLED".equals(settlementStatus)) {
            return new ExecutionPackOutcome(RuntimeExecutionStatus.COMPLETED,
                ControlledDeliveryResult.delivered("SETTLED", connector.responseDigest()));
        }
        if ("MISMATCH".equals(reconciliation) || "CRITICAL_MISMATCH".equals(reconciliation)
            || "RECONCILIATION_REQUIRED".equals(settlementStatus)) {
            return outcome(RuntimeExecutionStatus.REVIEW_REQUIRED, connector, "SETTLEMENT_RECONCILIATION_REQUIRED");
        }
        if ("FAILED".equals(settlementStatus)) {
            return outcome(RuntimeExecutionStatus.FAILED, connector, "SETTLEMENT_FAILED");
        }
        return outcome(RuntimeExecutionStatus.EGRESSING, connector, "SETTLEMENT_PENDING");
    }

    private String reconciliation(Map<String, Object> requestPayload, Map<?, ?> response, String status) {
        if (!"SETTLED".equals(status)) {
            return "WAIT";
        }
        Map<String, Object> expected = map(requestPayload.get("transaction"));
        Map<String, Object> actual = map(response.get("settledTransaction"));
        boolean criticalMismatch = CRITICAL_FIELDS.stream()
            .anyMatch(field -> !String.valueOf(expected.get(field)).equals(String.valueOf(actual.get(field))));
        if (criticalMismatch) {
            return "CRITICAL_MISMATCH";
        }
        return expected.equals(actual) ? "MATCH" : "MISMATCH";
    }

    private Map<String, Object> map(Object value) {
        Map<String, Object> result = new TreeMap<>();
        if (value instanceof Map<?, ?> map) {
            map.forEach((key, item) -> result.put(String.valueOf(key), item));
        }
        return result;
    }

    private String string(Object value) {
        return value instanceof String text && !text.isBlank() ? text : null;
    }

    private ExecutionPackOutcome outcome(RuntimeExecutionStatus status, ConnectorResult connector, String reason) {
        return new ExecutionPackOutcome(status, ControlledDeliveryResult.withheld(connector.responseDigest(), reason));
    }
}
