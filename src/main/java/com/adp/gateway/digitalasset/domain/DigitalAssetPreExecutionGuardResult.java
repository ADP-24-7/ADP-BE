package com.adp.gateway.digitalasset.domain;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import com.adp.gateway.common.error.ReasonCode;

public record DigitalAssetPreExecutionGuardResult(
    String executionId,
    String snapshotId,
    String status,
    Map<DigitalAssetArtifactControl, String> controlResults,
    List<ReasonCode> reasonCodes,
    String outboundPayloadDigest,
    String providerPayloadDigest,
    OffsetDateTime evaluatedAt
) {
    public DigitalAssetPreExecutionGuardResult {
        controlResults = Map.copyOf(controlResults);
        reasonCodes = List.copyOf(reasonCodes);
    }

    public boolean permitsEgress() {
        return "PASSED".equals(status);
    }
}
