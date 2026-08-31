package com.adp.gateway.transform.application;

import java.time.Duration;

import com.adp.gateway.retrieval.domain.DataClass;

public record VaultTokenRequest(
    String mappingScope,
    DataClass dataClass,
    String sourceValueDigest,
    String keyVersion,
    String mappingVersion,
    Duration ttl
) {
}
