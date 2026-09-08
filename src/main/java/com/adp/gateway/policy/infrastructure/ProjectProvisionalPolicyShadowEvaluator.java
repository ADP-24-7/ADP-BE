package com.adp.gateway.policy.infrastructure;

import java.util.List;
import java.util.Set;

import com.adp.gateway.context.application.CanonicalValueHasher;
import com.adp.gateway.decision.domain.FinalAction;
import com.adp.gateway.policy.application.PolicyLifecycleException;
import com.adp.gateway.policy.application.PolicyShadowEvaluator;
import com.adp.gateway.policy.domain.PolicyLifecycleRecord;
import com.adp.gateway.policy.domain.PolicyShadowOutcome;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "adp.local-fixtures.enabled", havingValue = "true")
public class ProjectProvisionalPolicyShadowEvaluator implements PolicyShadowEvaluator {
    private static final Set<String> CASES = Set.of("GOLDEN_ALLOW", "FAILURE_BLOCK");
    private final CanonicalValueHasher hasher;

    public ProjectProvisionalPolicyShadowEvaluator(CanonicalValueHasher hasher) {
        this.hasher = hasher;
    }

    @Override
    public PolicyShadowOutcome evaluate(PolicyLifecycleRecord artifact, String evaluationCaseId) {
        if (!CASES.contains(evaluationCaseId)) {
            throw new PolicyLifecycleException("POLICY_SHADOW_CASE_NOT_FOUND");
        }
        boolean restrictive = Integer.parseInt(artifact.artifactDigest().substring(0, 1), 16) >= 8;
        FinalAction action = restrictive ? FinalAction.BLOCK : FinalAction.ALLOW;
        return new PolicyShadowOutcome(
            evaluationCaseId, "1.0.0", hasher.hash("policy-shadow-case/1|" + evaluationCaseId), action,
            List.of(restrictive ? "FIXTURE_POLICY_BLOCK" : "FIXTURE_POLICY_ALLOW"),
            restrictive ? List.of("MANUAL_REVIEW") : List.of(), "NONE", "dest_shadow_fixture_v1"
        );
    }
}
