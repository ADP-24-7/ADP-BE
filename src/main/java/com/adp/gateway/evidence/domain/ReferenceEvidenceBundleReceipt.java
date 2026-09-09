package com.adp.gateway.evidence.domain;

import java.time.OffsetDateTime;

public record ReferenceEvidenceBundleReceipt(
    String bundleId,
    String bundleVersion,
    String schemaVersion,
    String analysisVersion,
    String contentDigest,
    int evidenceCount,
    OffsetDateTime snapshotAt,
    OffsetDateTime ingestedAt,
    boolean replayed
) {
}
