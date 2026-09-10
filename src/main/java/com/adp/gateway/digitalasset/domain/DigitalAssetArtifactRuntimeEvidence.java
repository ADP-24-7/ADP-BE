package com.adp.gateway.digitalasset.domain;

import java.time.OffsetDateTime;

public record DigitalAssetArtifactRuntimeEvidence(
    String executionId,
    String requestId,
    String traceId,
    String runtimeStatus,
    String finalAction,
    String snapshotId,
    String snapshotDigest,
    String approvedPolicySnapshotId,
    String approvedPolicyVersion,
    String approvedPolicyDigest,
    String destinationProfileId,
    String destinationProfileVersion,
    String destinationProfileDigest,
    String postExecutionStatus,
    String externalStatus,
    String providerStatus,
    String receiptStatus,
    String finalityStatus,
    OffsetDateTime selectedAt,
    OffsetDateTime observedAt,
    String tracePath,
    String evidencePath
) { }
