package com.adp.gateway.digitalasset.infrastructure;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.time.OffsetDateTime;

import com.adp.gateway.common.contract.RuntimeRequestContext;
import com.adp.gateway.connector.application.RuntimeConnectorPort;
import com.adp.gateway.connector.domain.ConnectorResult;
import com.adp.gateway.connector.domain.ConnectorStatus;
import com.adp.gateway.context.application.CanonicalValueHasher;
import com.adp.gateway.decision.domain.RuntimeDecision;
import com.adp.gateway.digitalasset.domain.DigitalAssetCanonicalContract;
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
        String externalReference = "asset_tx_" + UUID.randomUUID();
        String transactionHash = "0x" + UUID.randomUUID().toString().replace("-", "");
        Map<String, Object> expected = transaction(request.payload());
        String assetSymbol = String.valueOf(expected.get("assetSymbol"));
        if ("asset-sent-unknown".equals(assetSymbol)) {
            stateStore.record(request.providerCorrelationKey(), ConnectorStatus.ACKNOWLEDGED);
            return new ConnectorResult("con_" + UUID.randomUUID(), "fake-digital-asset-platform",
                ConnectorStatus.SENT_UNKNOWN, outbound.outboundPayloadId(), outbound.candidatePayloadDigest(),
                null, null, null);
        }
        stateStore.record(request.providerCorrelationKey(), ConnectorStatus.ACKNOWLEDGED);
        String externalStatus = "asset-settling".equals(assetSymbol) ? "SETTLING" : "SETTLED";
        Map<String, Object> actual = new TreeMap<>(expected);
        if ("asset-critical-mismatch".equals(assetSymbol)) {
            actual.put("amount", "999999");
        }
        if ("asset-mismatch".equals(assetSymbol)) {
            actual.put("recipientAddress", "wallet-provider-mismatch");
        }
        Map<String, Object> response = new HashMap<>();
        response.put("externalRequestId", "asset-correlation-mismatch".equals(assetSymbol)
            ? "asset_req_wrong_" + UUID.randomUUID()
            : request.providerCorrelationKey());
        response.put("externalReference", externalReference);
        response.put("transactionHash", transactionHash);
        response.put("externalStatus", externalStatus);
        response.put("providerStatus", "ACKNOWLEDGED");
        response.put("receiptStatus", "SETTLED".equals(externalStatus) ? "SUCCESS" : "PENDING");
        response.put("finalityStatus", "SETTLED".equals(externalStatus) ? "FINALIZED" : "UNCONFIRMED");
        response.put("executedChainId", actual.get("chainId"));
        response.put("executedRecipientAddress", actual.get("recipientAddress"));
        response.put("executedAssetKind", actual.get("assetKind"));
        response.put("executedAssetSymbol", actual.get("assetSymbol"));
        response.put("executedAssetContractAddress", actual.get("assetContractAddress"));
        response.put("executedAmount", actual.get("amount"));
        response.put("nativeValue", "0");
        response.put("operation", actual.get("operation"));
        response.put("tokenId", actual.get("tokenId"));
        response.put("tokenTransferEvidenceRef", "token-transfer:" + externalReference);
        response.put("internalTraceEvidenceRef", null);
        OffsetDateTime executedAt = OffsetDateTime.parse("2026-09-08T00:00:00Z");
        response.put("executedAt", executedAt.toString());
        response.put("finalizedAt", "SETTLED".equals(externalStatus) ? executedAt.plusMinutes(1).toString() : null);
        if ("asset-unexpected-field".equals(assetSymbol)) {
            response.put("customer-100-sensitive-value", "unexpected");
        }
        String responseDigest = hasher.hash(json(response));
        return new ConnectorResult("con_" + UUID.randomUUID(), "fake-digital-asset-platform",
            ConnectorStatus.ACKNOWLEDGED, outbound.outboundPayloadId(), outbound.candidatePayloadDigest(),
            responseDigest, DigitalAssetCanonicalContract.EXTERNAL_RESULT_SCHEMA_VERSION, response);
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
