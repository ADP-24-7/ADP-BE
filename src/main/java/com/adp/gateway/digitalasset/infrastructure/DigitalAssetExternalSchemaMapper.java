package com.adp.gateway.digitalasset.infrastructure;

import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import com.adp.gateway.context.application.CanonicalValueHasher;
import com.adp.gateway.egress.application.ExternalSchemaMapper;
import com.adp.gateway.egress.domain.DestinationProfile;
import com.adp.gateway.egress.domain.ExecutionPackType;
import com.adp.gateway.egress.domain.OutboundCandidatePayload;
import com.adp.gateway.egress.domain.ProviderRequestPayload;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class DigitalAssetExternalSchemaMapper implements ExternalSchemaMapper {
    private final ObjectMapper objectMapper;
    private final CanonicalValueHasher hasher;

    public DigitalAssetExternalSchemaMapper(ObjectMapper objectMapper, CanonicalValueHasher hasher) {
        this.objectMapper = objectMapper;
        this.hasher = hasher;
    }

    @Override
    public ExecutionPackType supportedPack() {
        return ExecutionPackType.DIGITAL_ASSET;
    }

    @Override
    public ProviderRequestPayload map(DestinationProfile profile, OutboundCandidatePayload outbound) {
        Map<String, Object> fields = new TreeMap<>();
        outbound.fields().forEach(field -> fields.put(providerField(field.path()), field.value()));
        String externalRequestId = "asset_req_" + UUID.randomUUID();
        Map<String, Object> payload = new TreeMap<>();
        payload.put("externalRequestId", externalRequestId);
        payload.put("schemaVersion", profile.schemaVersion());
        payload.put("transaction", fields);
        try {
            return new ProviderRequestPayload(externalRequestId, outbound.outboundPayloadId(),
                profile.providerProfileId(), profile.schemaVersion(),
                hasher.hash(objectMapper.writeValueAsString(payload)), fields.size(), payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Digital asset provider request could not be canonicalized", exception);
        }
    }

    private String providerField(String path) {
        return switch (path) {
            case "$.input.customerId" -> "customerToken";
            case "$.input.accountId" -> "accountToken";
            case "$.input.walletAddress" -> "walletAddress";
            case "$.input.assetId" -> "assetId";
            case "$.input.amount" -> "amount";
            case "$.input.kycStatus" -> "kycStatus";
            case "$.input.amlStatus" -> "amlStatus";
            case "$.input.walletVerified" -> "walletVerified";
            default -> throw new IllegalArgumentException("Unsupported Digital Asset provider field");
        };
    }
}
