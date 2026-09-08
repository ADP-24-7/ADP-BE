package com.adp.gateway.runtime.api;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import com.adp.gateway.common.error.ReasonCode;
import com.adp.gateway.digitalasset.domain.DigitalAssetArtifactControl;
import com.adp.gateway.digitalasset.domain.DigitalAssetPreExecutionGuardResult;

public record DigitalAssetPreExecutionGuardResponse(
    String snapshotId,
    String status,
    Map<DigitalAssetArtifactControl, String> controlResults,
    List<ReasonCode> reasonCodes,
    String outboundPayloadDigest,
    String providerPayloadDigest,
    OffsetDateTime evaluatedAt
) {
    public static DigitalAssetPreExecutionGuardResponse from(DigitalAssetPreExecutionGuardResult result) {
        return result == null ? null : new DigitalAssetPreExecutionGuardResponse(
            result.snapshotId(), result.status(), result.controlResults(), result.reasonCodes(),
            result.outboundPayloadDigest(), result.providerPayloadDigest(), result.evaluatedAt()
        );
    }
}
