package com.adp.gateway.policy.application;

public record HandoffDigest(
    String algorithm,
    String value
) {
}
