package com.adp.gateway.policyharness.domain;

public record PolicyLayerReference(
    String layer,
    String referenceId,
    String version,
    String digest
) {
}
