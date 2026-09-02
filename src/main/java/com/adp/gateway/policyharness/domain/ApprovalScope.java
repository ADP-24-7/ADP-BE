package com.adp.gateway.policyharness.domain;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

import com.adp.gateway.auth.domain.AdpRole;

public record ApprovalScope(
    String approvalReference,
    String approvalVersion,
    String approvalScopeDigest,
    String institutionId,
    String institutionPolicyVersion,
    String institutionPolicyDigest,
    String workloadId,
    String purposeCode,
    Set<AdpRole> allowedRoles,
    Set<String> allowedProcessingContexts,
    Set<String> allowedFields,
    String destinationProfileId,
    String destinationProfileVersion,
    OffsetDateTime effectiveAt,
    OffsetDateTime expiresAt,
    List<String> evidenceReferences
) {

    public ApprovalScope {
        allowedRoles = Set.copyOf(allowedRoles);
        allowedProcessingContexts = Set.copyOf(allowedProcessingContexts);
        allowedFields = Set.copyOf(allowedFields);
        evidenceReferences = List.copyOf(evidenceReferences);
    }

    public boolean isEffectiveAt(OffsetDateTime requestStartedAt) {
        return !effectiveAt.isAfter(requestStartedAt)
            && (expiresAt == null || expiresAt.isAfter(requestStartedAt));
    }
}
