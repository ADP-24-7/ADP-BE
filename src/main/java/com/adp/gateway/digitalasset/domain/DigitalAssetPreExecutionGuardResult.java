package com.adp.gateway.digitalasset.domain;

import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    private static final Set<String> CONTROL_STATUSES = Set.of("PASSED", "BLOCKED", "REVIEW_REQUIRED");

    public DigitalAssetPreExecutionGuardResult {
        if (!EnumSet.allOf(DigitalAssetArtifactControl.class).equals(controlResults.keySet())) {
            throw new IllegalArgumentException("DIGITAL_ASSET_PRE_EXECUTION_CONTROLS_INCOMPLETE");
        }
        if (!CONTROL_STATUSES.contains(status)
            || controlResults.values().stream().anyMatch(value -> !CONTROL_STATUSES.contains(value))) {
            throw new IllegalArgumentException("DIGITAL_ASSET_PRE_EXECUTION_STATUS_INVALID");
        }
        controlResults = Map.copyOf(controlResults);
        reasonCodes = List.copyOf(reasonCodes);
    }

    public boolean permitsEgress() {
        return "PASSED".equals(status);
    }
}
