package com.adp.gateway.egress.domain;

import com.adp.gateway.retrieval.domain.DataClass;

public record DestinationFieldContract(
    String path,
    DataClass dataClass,
    FieldObligation obligation,
    boolean required,
    boolean exactAllowed
) {
}
