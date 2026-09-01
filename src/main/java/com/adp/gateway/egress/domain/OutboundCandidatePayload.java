package com.adp.gateway.egress.domain;

import java.util.List;

public record OutboundCandidatePayload(
    String outboundPayloadId,
    String destinationProfileId,
    ExecutionPackType packType,
    String schemaVersion,
    String payloadDigest,
    List<OutboundCandidateField> fields
) {

    public OutboundCandidatePayload {
        fields = List.copyOf(fields);
    }

    public int fieldCount() {
        return fields.size();
    }
}
