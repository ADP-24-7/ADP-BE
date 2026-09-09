package com.adp.gateway.policy.application;

import java.util.Set;

import com.adp.gateway.policy.domain.PolicyShadowEvidence;

public interface PolicyShadowEvidencePersistence {
    PolicyShadowEvidence save(PolicyShadowEvidence evidence);

    PolicyShadowEvidence loadLatestForApproval(
        String institutionId,
        Set<String> allowedWorkloads,
        String candidateArtifactId,
        String candidateArtifactVersion,
        String shadowEvaluationId
    );
}
