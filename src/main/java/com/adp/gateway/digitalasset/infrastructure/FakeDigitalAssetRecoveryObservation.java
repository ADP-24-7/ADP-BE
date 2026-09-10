package com.adp.gateway.digitalasset.infrastructure;

import java.util.Map;

import com.adp.gateway.digitalasset.domain.ExternalExecutionResult;

record FakeDigitalAssetRecoveryObservation(
    Map<String, Object> requestPayload,
    ExternalExecutionResult executionResult
) {
    FakeDigitalAssetRecoveryObservation {
        requestPayload = Map.copyOf(requestPayload);
    }
}
