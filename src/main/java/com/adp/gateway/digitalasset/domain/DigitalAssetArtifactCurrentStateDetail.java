package com.adp.gateway.digitalasset.domain;

import java.time.OffsetDateTime;

public record DigitalAssetArtifactCurrentStateDetail(
    DigitalAssetArtifactCurrentStateItem artifact,
    String manifestSchemaVersion,
    String manifestReference,
    String canonicalContractVersion,
    String canonicalContractDigest,
    String runtimeControlVersion,
    String runtimeControlDigest,
    String crosswalkVersion,
    String crosswalkDigest,
    int fileCount,
    String createdBy,
    String ingestedBy,
    OffsetDateTime createdAt,
    DigitalAssetActiveArtifact activeSelection,
    DigitalAssetArtifactRuntimeEvidence latestRuntimeEvidence,
    String policyHistoryPath
) { }
