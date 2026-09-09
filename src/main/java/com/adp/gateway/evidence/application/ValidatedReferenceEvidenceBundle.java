package com.adp.gateway.evidence.application;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import com.adp.gateway.evidence.domain.ReferenceEvidenceStatus;
import com.adp.gateway.evidence.domain.ReferenceEvidenceType;

public record ValidatedReferenceEvidenceBundle(
    String schemaVersion,
    String bundleId,
    String bundleVersion,
    OffsetDateTime snapshotAt,
    String analysisVersion,
    String contentDigest,
    List<Item> evidence
) {
    public ValidatedReferenceEvidenceBundle {
        evidence = List.copyOf(evidence);
    }

    public record Item(
        String evidenceId,
        String evidenceVersion,
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
        String contentDigest
    ) {
        public Item {
            workloadRefs = List.copyOf(workloadRefs);
            policyArtifactRefs = List.copyOf(policyArtifactRefs);
        }
    }
}
