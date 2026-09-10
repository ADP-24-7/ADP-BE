package com.adp.gateway.evidence.application;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Set;

import com.adp.gateway.auth.domain.AdpRole;
import com.adp.gateway.auth.domain.AuthPrincipal;
import com.adp.gateway.evidence.domain.ReferenceEvidence;
import com.adp.gateway.evidence.domain.ReferenceEvidenceBundleReceipt;
import com.adp.gateway.evidence.domain.ReferenceEvidencePage;
import com.adp.gateway.evidence.domain.ReferenceEvidenceType;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReferenceEvidenceService {
    private final ReferenceEvidenceBundleValidator validator;
    private final ReferenceEvidencePersistence persistence;
    private final Clock clock;

    public ReferenceEvidenceService(
        ReferenceEvidenceBundleValidator validator,
        ReferenceEvidencePersistence persistence,
        Clock clock
    ) {
        this.validator = validator;
        this.persistence = persistence;
        this.clock = clock;
    }

    @Transactional
    public ReferenceEvidenceBundleReceipt ingest(AuthPrincipal principal, JsonNode document) {
        requireRole(principal, AdpRole.PRIVILEGED_OPERATOR);
        ValidatedReferenceEvidenceBundle bundle = validator.validate(document);
        Set<String> referencedWorkloads = bundle.evidence().stream()
            .flatMap(item -> item.workloadRefs().stream())
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (referencedWorkloads.stream().anyMatch(workload -> !principal.canAccessWorkload(workload))) {
            throw rejected("REFERENCE_EVIDENCE_FORBIDDEN");
        }

        persistence.lockBundle(principal.institutionId(), bundle.bundleId(), bundle.bundleVersion());
        var existing = persistence.findBundle(
            principal.institutionId(), bundle.bundleId(), bundle.bundleVersion()
        );
        if (existing.isPresent()) {
            if (existing.get().contentDigest().equals(bundle.contentDigest())) {
                ReferenceEvidenceBundleReceipt receipt = existing.get();
                return new ReferenceEvidenceBundleReceipt(
                    receipt.bundleId(), receipt.bundleVersion(), receipt.schemaVersion(),
                    receipt.analysisVersion(), receipt.contentDigest(), receipt.evidenceCount(),
                    receipt.snapshotAt(), receipt.ingestedAt(), true
                );
            }
            throw rejected("REFERENCE_EVIDENCE_CONFLICT");
        }
        return persistence.create(
            principal.institutionId(), principal.principalId(), bundle, OffsetDateTime.now(clock)
        );
    }

    public ReferenceEvidencePage search(
        AuthPrincipal principal,
        ReferenceEvidenceType evidenceType,
        String workloadId,
        String query,
        int limit,
        int offset
    ) {
        requireReader(principal);
        validateSearch(workloadId, query, limit, offset);
        if (workloadId != null && !principal.canAccessWorkload(workloadId)) {
            throw rejected("REFERENCE_EVIDENCE_FORBIDDEN");
        }
        return persistence.search(
            principal.institutionId(), principal.workloadIds(), evidenceType,
            workloadId, normalize(query), limit, offset
        );
    }

    public ReferenceEvidence load(
        AuthPrincipal principal, String evidenceId, String evidenceVersion
    ) {
        requireReader(principal);
        return persistence.find(
            principal.institutionId(), principal.workloadIds(), evidenceId, evidenceVersion
        ).orElseThrow(() -> rejected("REFERENCE_EVIDENCE_NOT_FOUND"));
    }

    private void validateSearch(String workloadId, String query, int limit, int offset) {
        if (limit < 1 || limit > 100 || offset < 0 || tooLong(workloadId, 120)
            || tooLong(query, 120)) {
            throw rejected("REFERENCE_EVIDENCE_SEARCH_INVALID");
        }
    }

    private boolean tooLong(String value, int max) {
        return value != null && value.length() > max;
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void requireReader(AuthPrincipal principal) {
        requireInstitution(principal);
        if (!principal.hasRole(AdpRole.OPERATOR) && !principal.hasRole(AdpRole.PRIVILEGED_OPERATOR)
            && !principal.hasRole(AdpRole.AUDITOR)) {
            throw rejected("REFERENCE_EVIDENCE_FORBIDDEN");
        }
    }

    private void requireRole(AuthPrincipal principal, AdpRole role) {
        requireInstitution(principal);
        if (!principal.hasRole(role)) {
            throw rejected("REFERENCE_EVIDENCE_FORBIDDEN");
        }
    }

    private void requireInstitution(AuthPrincipal principal) {
        if (principal == null || principal.institutionId() == null || principal.institutionId().isBlank()) {
            throw rejected("REFERENCE_EVIDENCE_FORBIDDEN");
        }
    }

    private ReferenceEvidenceException rejected(String reasonCode) {
        return new ReferenceEvidenceException(reasonCode);
    }
}
