package com.adp.gateway.observability.domain;

import java.time.OffsetDateTime;

public record PolicyOperationEvent(
    String eventId,
    PolicyEventCategory category,
    String eventType,
    String executionPack,
    String workloadId,
    String purposeCode,
    String artifactId,
    String artifactVersion,
    String artifactDigest,
    String previousArtifactId,
    String previousArtifactVersion,
    String previousArtifactDigest,
    Long artifactRevision,
    Long selectionRevision,
    String actorId,
    String reasonCode,
    OffsetDateTime occurredAt
) {
    public enum PolicyEventCategory {
        LIFECYCLE_TRANSITION,
        CURRENT_SELECTION
    }
}
