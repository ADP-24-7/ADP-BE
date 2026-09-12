package com.adp.gateway.evidence.application;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;

import com.adp.gateway.auth.domain.AdpRole;
import com.adp.gateway.auth.domain.AuthPrincipal;
import com.adp.gateway.evidence.domain.ReferenceEvidencePolicyLineage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReferenceEvidenceLineageService {
    private final ReferenceEvidenceService evidenceService;
    private final ReferenceEvidenceLineagePersistence persistence;
    private final Clock clock;

    public ReferenceEvidenceLineageService(
        ReferenceEvidenceService evidenceService,
        ReferenceEvidenceLineagePersistence persistence,
        Clock clock
    ) {
        this.evidenceService = evidenceService;
        this.persistence = persistence;
        this.clock = clock;
    }

    @Transactional
    public List<ReferenceEvidencePolicyLineage> bind(
        AuthPrincipal principal,
        String evidenceId,
        String evidenceVersion,
        String artifactId,
        String artifactVersion,
        String sourceDigest
    ) {
        requireRole(principal, AdpRole.PRIVILEGED_OPERATOR);
        evidenceService.load(principal, evidenceId, evidenceVersion);
        boolean found = persistence.bind(
            principal.institutionId(), evidenceId, evidenceVersion,
            artifactId, artifactVersion, sourceDigest,
            principal.principalId(), OffsetDateTime.now(clock)
        );
        if (!found) {
            throw new ReferenceEvidenceException("REFERENCE_EVIDENCE_NOT_FOUND");
        }
        return load(principal, artifactId, artifactVersion);
    }

    public List<ReferenceEvidencePolicyLineage> load(
        AuthPrincipal principal, String artifactId, String artifactVersion
    ) {
        requireReader(principal);
        return persistence.findByPolicyArtifact(
            principal.institutionId(), principal.workloadIds(), artifactId, artifactVersion
        );
    }

    private void requireReader(AuthPrincipal principal) {
        requireInstitution(principal);
        if (!principal.hasRole(AdpRole.OPERATOR) && !principal.hasRole(AdpRole.PRIVILEGED_OPERATOR)
            && !principal.hasRole(AdpRole.AUDITOR)) {
            throw new ReferenceEvidenceException("REFERENCE_EVIDENCE_FORBIDDEN");
        }
    }

    private void requireRole(AuthPrincipal principal, AdpRole role) {
        requireInstitution(principal);
        if (!principal.hasRole(role)) {
            throw new ReferenceEvidenceException("REFERENCE_EVIDENCE_FORBIDDEN");
        }
    }

    private void requireInstitution(AuthPrincipal principal) {
        if (principal == null || principal.institutionId() == null
            || principal.institutionId().isBlank()) {
            throw new ReferenceEvidenceException("REFERENCE_EVIDENCE_FORBIDDEN");
        }
    }
}
