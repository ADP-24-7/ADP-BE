package com.adp.gateway.digitalasset.infrastructure;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import com.adp.gateway.common.contract.RuntimeRequestContext;
import com.adp.gateway.connector.application.RuntimeConnectorPort;
import com.adp.gateway.connector.domain.ConnectorResult;
import com.adp.gateway.connector.domain.ConnectorStatus;
import com.adp.gateway.context.application.CanonicalValueHasher;
import com.adp.gateway.decision.domain.RuntimeDecision;
import com.adp.gateway.egress.domain.ExecutionPackType;
import com.adp.gateway.egress.domain.OutboundCandidatePayload;
import com.adp.gateway.egress.domain.ProviderRequestPayload;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "adp.local-fixtures.enabled", havingValue = "true")
public class FakeDigitalAssetConnector implements RuntimeConnectorPort {
    private final ObjectMapper objectMapper;
    private final CanonicalValueHasher hasher;
    private final FakeDigitalAssetPlatformStateStore stateStore;

    public FakeDigitalAssetConnector(
        ObjectMapper objectMapper,
        CanonicalValueHasher hasher,
        FakeDigitalAssetPlatformStateStore stateStore
    ) {
        this.objectMapper = objectMapper;
        this.hasher = hasher;
        this.stateStore = stateStore;
    }

    @Override
    public ExecutionPackType supportedPack() {
        return ExecutionPackType.DIGITAL_ASSET;
    }

    @Override
    public ConnectorResult execute(RuntimeRequestContext context, RuntimeDecision decision,
                                   OutboundCandidatePayload outbound, ProviderRequestPayload request) {
        String externalTransactionId = "asset_tx_" + UUID.randomUUID();
        String settlementId = "settlement_" + UUID.randomUUID();
        Map<String, Object> expected = transaction(request.payload());
        String assetId = String.valueOf(expected.get("assetId"));
        if ("asset-sent-unknown".equals(assetId)) {
            stateStore.record(request.providerCorrelationKey(), ConnectorStatus.ACKNOWLEDGED);
            return new ConnectorResult("con_" + UUID.randomUUID(), "fake-digital-asset-platform",
                ConnectorStatus.SENT_UNKNOWN, outbound.outboundPayloadId(), outbound.candidatePayloadDigest(),
                null, null, null);
        }
        stateStore.record(request.providerCorrelationKey(), ConnectorStatus.ACKNOWLEDGED);
        String settlementStatus = "asset-settling".equals(assetId) ? "SETTLING" : "SETTLED";
        Map<String, Object> actual = new TreeMap<>(expected);
        if ("asset-critical-mismatch".equals(assetId)) {
            actual.put("amount", "999999");
        }
        Map<String, Object> response = new HashMap<>();
        response.put("externalRequestId", "asset-correlation-mismatch".equals(assetId)
            ? "asset_req_wrong_" + UUID.randomUUID()
            : request.providerCorrelationKey());
        response.put("externalTransactionId", externalTransactionId);
        response.put("settlementStatus", settlementStatus);
        response.put("settledTransaction", actual);
        if ("SETTLED".equals(settlementStatus)) {
            response.put("settlementId", settlementId);
        }
        String responseDigest = hasher.hash(json(response));
        return new ConnectorResult("con_" + UUID.randomUUID(), "fake-digital-asset-platform",
            ConnectorStatus.ACKNOWLEDGED, outbound.outboundPayloadId(), outbound.candidatePayloadDigest(),
            responseDigest, "digital-asset-settlement/v1", response);
    }

    private Map<String, Object> transaction(Map<String, Object> payload) {
        Object value = payload.get("transaction");
        if (!(value instanceof Map<?, ?> transaction)) {
            throw new IllegalArgumentException("Digital asset transaction payload is missing");
        }
        Map<String, Object> result = new TreeMap<>();
        transaction.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Digital asset response could not be canonicalized", exception);
        }
    }
}
