package com.adp.gateway.egress.domain;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;

public record ProviderRequestPayload(
    String providerRequestId,
    String outboundPayloadId,
    String providerProfileId,
    String schemaVersion,
    String canonicalPayloadDigest,
    int fieldCount,
    @JsonIgnore Map<String, Object> payload
) {

    public ProviderRequestPayload {
        payload = Map.copyOf(payload);
    }

    @Override
    public String toString() {
        return "ProviderRequestPayload[providerRequestId=%s, outboundPayloadId=%s, providerProfileId=%s, schemaVersion=%s, canonicalPayloadDigest=%s, fieldCount=%d, payload=<redacted>]"
            .formatted(
                providerRequestId,
                outboundPayloadId,
                providerProfileId,
                schemaVersion,
                canonicalPayloadDigest,
                fieldCount
            );
    }
}
