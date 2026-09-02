package com.adp.gateway.ai.infrastructure;

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
public class AiExternalSchemaMapper implements ExternalSchemaMapper {

    private final ObjectMapper objectMapper;
    private final CanonicalValueHasher hasher;

    public AiExternalSchemaMapper(ObjectMapper objectMapper, CanonicalValueHasher hasher) {
        this.objectMapper = objectMapper;
        this.hasher = hasher;
    }

    @Override
    public ProviderRequestPayload map(DestinationProfile destinationProfile, OutboundCandidatePayload outboundPayload) {
        if (destinationProfile.packType() != ExecutionPackType.AI) {
            throw new IllegalArgumentException("AI schema mapper cannot map a non-AI execution pack");
        }
        Map<String, Object> fields = new TreeMap<>();
        outboundPayload.fields().forEach(field -> fields.put(field.path(), field.value()));
        Map<String, Object> payload = new TreeMap<>();
        payload.put("context", fields);
        payload.put("schemaVersion", destinationProfile.schemaVersion());
        payload.put("tenant", destinationProfile.tenantId());
        try {
            String canonicalJson = objectMapper.writeValueAsString(payload);
            return new ProviderRequestPayload(
                "preq_" + UUID.randomUUID(),
                outboundPayload.outboundPayloadId(),
                destinationProfile.providerProfileId(),
                destinationProfile.schemaVersion(),
                hasher.hash(canonicalJson),
                fields.size(),
                payload
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("AI provider request could not be canonicalized", exception);
        }
    }
}
