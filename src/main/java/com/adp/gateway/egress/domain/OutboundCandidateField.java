package com.adp.gateway.egress.domain;

import java.util.List;

import com.adp.gateway.retrieval.domain.DataClass;
import com.adp.gateway.transform.domain.TransformStrategy;
import com.fasterxml.jackson.annotation.JsonIgnore;

public record OutboundCandidateField(
    String path,
    DataClass dataClass,
    TransformStrategy strategy,
    FieldObligation obligation,
    FieldTreatment treatment,
    String valueDigest,
    List<OutboundSensitiveFinding> sensitiveFindings,
    @JsonIgnore
    Object value
) {

    public OutboundCandidateField {
        sensitiveFindings = List.copyOf(sensitiveFindings);
    }
}
