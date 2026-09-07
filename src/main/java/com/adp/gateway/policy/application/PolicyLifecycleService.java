package com.adp.gateway.policy.application;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Set;

import com.adp.gateway.auth.domain.AdpRole;
import com.adp.gateway.auth.domain.AuthPrincipal;
import com.adp.gateway.egress.domain.ExecutionPackType;
import com.adp.gateway.policy.domain.PolicyLayer;
import com.adp.gateway.policy.domain.PolicyLifecycleRecord;
import com.adp.gateway.policy.domain.PolicyLifecycleStage;
import com.adp.gateway.policy.domain.PolicyLifecycleTransitionReason;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PolicyLifecycleService {
    private static final Set<PolicyLifecycleStage> PRIVILEGED_TARGETS = Set.of(
        PolicyLifecycleStage.APPROVED, PolicyLifecycleStage.ACTIVE, PolicyLifecycleStage.ROLLED_BACK
    );
    private final PolicyLifecyclePersistence persistence;
    private final PolicyLifecycleTransitionValidator transitionValidator;
    private final Clock clock;

    public PolicyLifecycleService(
        PolicyLifecyclePersistence persistence,
        PolicyLifecycleTransitionValidator transitionValidator,
        Clock clock
    ) {
        this.persistence = persistence;
        this.transitionValidator = transitionValidator;
        this.clock = clock;
    }

    @Transactional
    public PolicyLifecycleRecord create(
        AuthPrincipal principal,
        String artifactId,
        String artifactVersion,
        String artifactDigest,
        PolicyLayer layer,
        ExecutionPackType pack,
        String workloadId,
        String purposeCode
    ) {
        requireRole(principal, AdpRole.OPERATOR);
        requireScope(principal, workloadId);
        validateIdentity(artifactId, artifactVersion, artifactDigest, workloadId, purposeCode);
        OffsetDateTime now = OffsetDateTime.now(clock);
        return persistence.create(new PolicyLifecycleRecord(
            artifactId, artifactVersion, artifactDigest, principal.institutionId(), layer, pack,
            workloadId, purposeCode, PolicyLifecycleStage.DRAFT, principal.principalId(), 0, now, now
        ));
    }

    @Transactional
    public PolicyLifecycleRecord transition(
        AuthPrincipal principal,
        String artifactId,
        String artifactVersion,
        PolicyLifecycleStage target,
        PolicyLifecycleTransitionReason reason
    ) {
        PolicyLifecycleRecord current = persistence.load(artifactId, artifactVersion);
        requireInstitution(principal, current);
        requireScope(principal, current.workloadId());
        requireRole(principal, PRIVILEGED_TARGETS.contains(target) ? AdpRole.PRIVILEGED_OPERATOR : AdpRole.OPERATOR);
        if ((target == PolicyLifecycleStage.APPROVED || target == PolicyLifecycleStage.ACTIVE)
            && current.createdBy().equals(principal.principalId())) {
            throw new PolicyLifecycleException("POLICY_LIFECYCLE_MAKER_CHECKER_VIOLATION");
        }
        if (reason == null) {
            throw new PolicyLifecycleException("POLICY_LIFECYCLE_REASON_INVALID");
        }
        transitionValidator.validate(current.lifecycleStage(), target, reason);
        return persistence.transition(current, target, principal.principalId(), reason, OffsetDateTime.now(clock));
    }

    public PolicyLifecycleRecord load(AuthPrincipal principal, String artifactId, String artifactVersion) {
        PolicyLifecycleRecord record = persistence.load(artifactId, artifactVersion);
        requireInstitution(principal, record);
        requireScope(principal, record.workloadId());
        if (!principal.hasRole(AdpRole.OPERATOR) && !principal.hasRole(AdpRole.PRIVILEGED_OPERATOR)
            && !principal.hasRole(AdpRole.AUDITOR)) {
            throw new PolicyLifecycleException("POLICY_LIFECYCLE_FORBIDDEN");
        }
        return record;
    }

    private void validateIdentity(String id, String version, String digest, String workload, String purpose) {
        if (blank(id) || blank(version) || blank(workload) || blank(purpose)
            || digest == null || !digest.matches("[0-9a-f]{64}")) {
            throw new PolicyLifecycleException("POLICY_LIFECYCLE_ARTIFACT_INVALID");
        }
    }

    private void requireInstitution(AuthPrincipal principal, PolicyLifecycleRecord record) {
        if (principal.institutionId() == null || !principal.institutionId().equals(record.institutionId())) {
            throw new PolicyLifecycleException("POLICY_LIFECYCLE_FORBIDDEN");
        }
    }

    private void requireScope(AuthPrincipal principal, String workloadId) {
        if (!principal.canAccessWorkload(workloadId)) {
            throw new PolicyLifecycleException("POLICY_LIFECYCLE_FORBIDDEN");
        }
    }

    private void requireRole(AuthPrincipal principal, AdpRole role) {
        if (!principal.hasRole(role)) {
            throw new PolicyLifecycleException("POLICY_LIFECYCLE_FORBIDDEN");
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank() || value.length() > 120;
    }
}
