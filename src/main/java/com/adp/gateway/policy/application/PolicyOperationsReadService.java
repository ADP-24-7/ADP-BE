package com.adp.gateway.policy.application;

import java.util.Set;

import com.adp.gateway.auth.domain.AdpRole;
import com.adp.gateway.auth.domain.AuthPrincipal;
import com.adp.gateway.egress.domain.ExecutionPackType;
import com.adp.gateway.policy.domain.PolicyArtifactHistory;
import com.adp.gateway.policy.domain.PolicyArtifactPage;
import com.adp.gateway.policy.domain.PolicyLifecycleRecord;
import com.adp.gateway.policy.domain.PolicyLifecycleStage;
import org.springframework.stereotype.Service;

@Service
public class PolicyOperationsReadService {
    private static final Set<AdpRole> READER_ROLES = Set.of(
        AdpRole.OPERATOR, AdpRole.PRIVILEGED_OPERATOR, AdpRole.AUDITOR
    );
    private final PolicyOperationsReadPort readPort;
    private final PolicyLifecyclePersistence lifecyclePersistence;

    public PolicyOperationsReadService(
        PolicyOperationsReadPort readPort,
        PolicyLifecyclePersistence lifecyclePersistence
    ) {
        this.readPort = readPort;
        this.lifecyclePersistence = lifecyclePersistence;
    }

    public PolicyArtifactPage search(
        AuthPrincipal principal,
        ExecutionPackType executionPack,
        PolicyLifecycleStage lifecycleStage,
        String workloadId,
        String query,
        boolean attentionRequired,
        int limit,
        int offset
    ) {
        requireReader(principal);
        if (executionPack == ExecutionPackType.SAAS || limit < 1 || limit > 100 || offset < 0
            || tooLong(workloadId, 120) || tooLong(query, 120)) {
            throw new PolicyLifecycleException("POLICY_OPERATIONS_SEARCH_INVALID");
        }
        String normalizedWorkload = normalize(workloadId);
        if (normalizedWorkload != null && !principal.canAccessWorkload(normalizedWorkload)) {
            throw new PolicyLifecycleException("POLICY_LIFECYCLE_FORBIDDEN");
        }
        return readPort.search(
            principal.institutionId(), principal.workloadIds(), executionPack, lifecycleStage,
            normalizedWorkload, normalize(query), attentionRequired, limit, offset
        );
    }

    public PolicyArtifactHistory history(
        AuthPrincipal principal,
        String artifactId,
        String artifactVersion
    ) {
        requireReader(principal);
        PolicyLifecycleRecord artifact = lifecyclePersistence.load(
            principal.institutionId(), principal.workloadIds(), artifactId, artifactVersion
        );
        return readPort.history(artifact);
    }

    private void requireReader(AuthPrincipal principal) {
        if (principal == null || principal.institutionId() == null || principal.institutionId().isBlank()
            || READER_ROLES.stream().noneMatch(principal::hasRole)) {
            throw new PolicyLifecycleException("POLICY_LIFECYCLE_FORBIDDEN");
        }
    }

    private boolean tooLong(String value, int max) {
        return value != null && value.length() > max;
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
