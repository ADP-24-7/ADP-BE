package com.adp.gateway.evidence.application;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

import com.adp.gateway.evidence.domain.ReferenceEvidencePolicyLineage;

public interface ReferenceEvidenceLineagePersistence {
    boolean bind(
        String institutionId,
        String evidenceId,
        String evidenceVersion,
        String artifactId,
        String artifactVersion,
        String sourceDigest,
        String actorId,
        OffsetDateTime boundAt
    );

    List<ReferenceEvidencePolicyLineage> findByPolicyArtifact(
        String institutionId,
        Set<String> allowedWorkloads,
        String artifactId,
        String artifactVersion
    );
}
