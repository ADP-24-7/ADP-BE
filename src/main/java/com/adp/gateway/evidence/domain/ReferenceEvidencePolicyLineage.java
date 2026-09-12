package com.adp.gateway.evidence.domain;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import com.adp.gateway.egress.domain.ExecutionPackType;
import com.adp.gateway.policy.domain.PolicyLifecycleStage;

public record ReferenceEvidencePolicyLineage(
    String regulatoryEvidenceId,
    String sourceVersion,
    String sourceDigest,
    String lawName,
    String authority,
    String officialSource,
    String sourceUrl,
    String applicableArticles,
    LocalDate effectiveDate,
    String policyArtifactId,
    String policyVersion,
    PolicyLifecycleStage lifecycleState,
    ExecutionPackType executionPack,
    String workloadId,
    String purposeCode,
    String reviewStatus,
    OffsetDateTime boundAt
) {
}
