package com.adp.gateway.decision.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.List;

import com.adp.gateway.common.error.ReasonCode;
import com.adp.gateway.context.application.CanonicalValueHasher;
import com.adp.gateway.decision.domain.FinalAction;
import com.adp.gateway.decision.domain.RuntimeAuthorizationResult;
import com.adp.gateway.decision.domain.RuntimeDecision;
import com.adp.gateway.egress.domain.ExecutionPackType;
import com.adp.gateway.policy.domain.ApplicabilityResult;
import com.adp.gateway.policy.domain.PolicyAction;
import org.junit.jupiter.api.Test;

class ExecutionPackPolicyGateResolverTests {

    private final ExecutionPackPolicyGateResolver resolver =
        new ExecutionPackPolicyGateResolver(List.of(), new CanonicalValueHasher());

    @Test
    void blocksWhenRequiredDigitalAssetGateIsNotConfigured() {
        var evaluation = resolver.evaluate(
            ExecutionPackType.DIGITAL_ASSET, null, null, baseline(FinalAction.ALLOW), OffsetDateTime.now()
        ).orElseThrow();

        assertThat(evaluation.profileId()).isEqualTo("NOT_CONFIGURED");
        assertThat(evaluation.profileAction()).isEqualTo(FinalAction.BLOCK);
        assertThat(evaluation.finalAction()).isEqualTo(FinalAction.BLOCK);
        assertThat(evaluation.reasonCodes()).containsExactly(ReasonCode.EXECUTION_PACK_POLICY_GATE_NOT_CONFIGURED);
        assertThat(evaluation.decision().finalAction()).isEqualTo(FinalAction.BLOCK);
    }

    @Test
    void leavesNonRequiredPackWithoutAnAdditionalEvaluation() {
        assertThat(resolver.evaluate(
            ExecutionPackType.AI, null, null, baseline(FinalAction.ALLOW), OffsetDateTime.now()
        )).isEmpty();
    }

    @Test
    void finalActionOrderingIsMonotonic() {
        assertThat(FinalAction.TRANSFORM.isAtLeastAsRestrictiveAs(FinalAction.ALLOW)).isTrue();
        assertThat(FinalAction.REVIEW.isAtLeastAsRestrictiveAs(FinalAction.TRANSFORM)).isTrue();
        assertThat(FinalAction.BLOCK.isAtLeastAsRestrictiveAs(FinalAction.REVIEW)).isTrue();
        assertThat(FinalAction.ALLOW.isAtLeastAsRestrictiveAs(FinalAction.BLOCK)).isFalse();
    }

    private RuntimeDecision baseline(FinalAction action) {
        return new RuntimeDecision(
            "decision-1", PolicyAction.ALLOW, action, List.of(ReasonCode.POLICY_ALLOW),
            RuntimeAuthorizationResult.ALLOWED, ApplicabilityResult.APPLICABLE,
            List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
            "policy-v1", "a".repeat(64), "b".repeat(64), null
        );
    }
}
