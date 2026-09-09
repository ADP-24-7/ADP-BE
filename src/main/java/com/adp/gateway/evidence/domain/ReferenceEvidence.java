package com.adp.gateway.evidence.domain;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record ReferenceEvidence(
    String evidenceId,
    String evidenceVersion,
    String bundleId,
    String bundleVersion,
    ReferenceEvidenceType evidenceType,
    String authority,
    String title,
    String sourceRef,
    String sourceUrl,
    LocalDate sourceDate,
    LocalDate effectiveFrom,
    LocalDate effectiveTo,
    String claimScope,
    String claimSummary,
    String sourceLocator,
    String analysisVersion,
    ReferenceEvidenceStatus status,
    List<String> workloadRefs,
    List<String> policyArtifactRefs,
    String contentDigest,
    OffsetDateTime createdAt
) {
    public ReferenceEvidence {
        workloadRefs = List.copyOf(workloadRefs);
        policyArtifactRefs = List.copyOf(policyArtifactRefs);
    }
}
