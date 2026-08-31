package com.adp.gateway.policy.application;

public record HandoffReference(
    String refId,
    String refType,
    String version
) {
}
