package com.adp.gateway.digitalasset.infrastructure;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Map;
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
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "adp.local-fixtures.enabled", havingValue = "true")
public class FakeDigitalAssetConnector implements RuntimeConnectorPort {
    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;
    private final CanonicalValueHasher hasher;
    private final Clock clock;

    public FakeDigitalAssetConnector(JdbcClient jdbcClient, ObjectMapper objectMapper, CanonicalValueHasher hasher, Clock clock) {
        this.jdbcClient = jdbcClient;
        this.objectMapper = objectMapper;
        this.hasher = hasher;
        this.clock = clock;
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
        Map<String, Object> response = Map.of(
            "answer", "SETTLED",
            "externalTransactionId", externalTransactionId,
            "settlementId", settlementId,
            "settlementStatus", "SETTLED",
            "reconciliationResult", "MATCH"
        );
        String responseDigest = hasher.hash(json(response));
        OffsetDateTime now = OffsetDateTime.now(clock);
        jdbcClient.sql("""
            insert into runtime.digital_asset_transaction (
                execution_id, external_request_id, external_transaction_id, settlement_id,
                settlement_status, reconciliation_result, provider_response_digest, created_at, updated_at
            )
            select oc.execution_id, :externalRequestId, :externalTransactionId, :settlementId,
                   'SETTLED', 'MATCH', :responseDigest, :now, :now
            from runtime.outbound_candidate oc
            where oc.outbound_payload_id = :outboundPayloadId
            """)
            .param("externalRequestId", request.providerCorrelationKey())
            .param("externalTransactionId", externalTransactionId)
            .param("settlementId", settlementId)
            .param("responseDigest", responseDigest)
            .param("now", now)
            .param("outboundPayloadId", outbound.outboundPayloadId())
            .update();
        return new ConnectorResult("con_" + UUID.randomUUID(), "fake-digital-asset-platform",
            ConnectorStatus.COMPLETED, outbound.outboundPayloadId(), outbound.candidatePayloadDigest(),
            responseDigest, "digital-asset-settlement/v1", response);
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Digital asset response could not be canonicalized", exception);
        }
    }
}
