package com.adp.gateway.egress.domain;

import java.util.List;

public record OutboundCandidatePayload(
    String outboundPayloadId,
    String destinationProfileId,
    String destinationProfileVersion,
    String destinationProfileDigest,
    ExecutionPackType packType,
    String schemaVersion,
    String candidatePayloadDigest,
    List<OutboundCandidateField> fields
) {

    public OutboundCandidatePayload {
        fields = List.copyOf(fields);
    }

    public int fieldCount() {
        return fields.size();
    }
}
