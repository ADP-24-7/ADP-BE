package com.adp.gateway.transform.application;

import java.time.Duration;

import com.adp.gateway.retrieval.domain.DataClass;

public record VaultTokenRequest(
    TransformScope transformScope,
    DataClass dataClass,
    String sourceValueDigest,
    String keyVersion,
    String mappingVersion,
    Duration ttl
) {

    public String mappingScope() {
        return transformScope.scopeId();
    }
}
