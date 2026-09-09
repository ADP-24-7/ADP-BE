package com.adp.gateway.evidence.application;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Set;

import com.adp.gateway.evidence.domain.ReferenceEvidence;
import com.adp.gateway.evidence.domain.ReferenceEvidenceBundleReceipt;
import com.adp.gateway.evidence.domain.ReferenceEvidencePage;
import com.adp.gateway.evidence.domain.ReferenceEvidenceStatus;
import com.adp.gateway.evidence.domain.ReferenceEvidenceType;

public interface ReferenceEvidencePersistence {
    void lockBundle(String institutionId, String bundleId, String bundleVersion);

    Optional<ReferenceEvidenceBundleReceipt> findBundle(
        String institutionId, String bundleId, String bundleVersion
    );

    ReferenceEvidenceBundleReceipt create(
        String institutionId,
        String actorId,
        ValidatedReferenceEvidenceBundle bundle,
        OffsetDateTime ingestedAt
    );

    ReferenceEvidencePage search(
        String institutionId,
        Set<String> allowedWorkloads,
        ReferenceEvidenceType evidenceType,
        String workloadId,
        String query,
        int limit,
        int offset
    );

    Optional<ReferenceEvidence> find(
        String institutionId,
        Set<String> allowedWorkloads,
        String evidenceId,
        String evidenceVersion
    );
}
