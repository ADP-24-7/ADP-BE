package com.adp.gateway.egress.domain;

public record DestinationBinding(
    String workloadId,
    String purposeCode
) {
}
