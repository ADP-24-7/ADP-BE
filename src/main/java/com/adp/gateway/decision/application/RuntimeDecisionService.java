package com.adp.gateway.decision.application;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.adp.gateway.common.contract.RuntimeRequestContext;
import com.adp.gateway.common.error.ReasonCode;
import com.adp.gateway.decision.domain.DecisionAction;
import com.adp.gateway.decision.domain.RuntimeAuthorizationResult;
import com.adp.gateway.decision.domain.RuntimeDecision;
import com.adp.gateway.policy.domain.ApplicabilityResult;
import com.adp.gateway.policy.domain.PolicySnapshot;
import org.springframework.stereotype.Service;

@Service
public class RuntimeDecisionService {

    private final MonotonicDecisionCombiner decisionCombiner;

    public RuntimeDecisionService(MonotonicDecisionCombiner decisionCombiner) {
        this.decisionCombiner = decisionCombiner;
    }

    public RuntimeDecision decide(
        RuntimeRequestContext context,
        PolicySnapshot snapshot,
        RuntimeAuthorizationResult authorizationResult,
        ApplicabilityResult applicabilityResult
    ) {
        DecisionAction runtimeAction = runtimeAction(authorizationResult, applicabilityResult);
        DecisionAction finalAction = decisionCombiner.combine(snapshot.evaluation().policyAction(), runtimeAction);
        List<ReasonCode> reasonCodes = reasonCodes(finalAction, authorizationResult, applicabilityResult);
        String decisionIdentity = String.join(
            ":",
            context.requestId(),
            context.workloadId(),
            snapshot.policyVersion(),
            snapshot.snapshotDigest(),
            snapshot.evaluation().policyAction().name(),
            authorizationResult.name(),
            applicabilityResult.name(),
            finalAction.name()
        );

        return new RuntimeDecision(
            "dec_" + UUID.nameUUIDFromBytes(decisionIdentity.getBytes(StandardCharsets.UTF_8)),
            snapshot.evaluation().policyAction(),
            finalAction,
            reasonCodes,
            authorizationResult,
            applicabilityResult,
            snapshot.matchedRuleIds(),
            snapshot.policyVersion(),
            snapshot.snapshotDigest(),
            snapshot.sourceArtifact().artifactId()
        );
    }

    private DecisionAction runtimeAction(
        RuntimeAuthorizationResult authorizationResult,
        ApplicabilityResult applicabilityResult
    ) {
        if (authorizationResult == RuntimeAuthorizationResult.DENIED) {
            return DecisionAction.BLOCK;
        }
        return switch (applicabilityResult) {
            case APPLICABLE -> DecisionAction.ALLOW;
            case NOT_APPLICABLE, CONFLICT, INCOMPLETE -> DecisionAction.REVIEW;
        };
    }

    private List<ReasonCode> reasonCodes(
        DecisionAction finalAction,
        RuntimeAuthorizationResult authorizationResult,
        ApplicabilityResult applicabilityResult
    ) {
        List<ReasonCode> reasonCodes = new ArrayList<>();
        if (authorizationResult == RuntimeAuthorizationResult.DENIED) {
            reasonCodes.add(ReasonCode.RUNTIME_AUTHORIZATION_DENIED);
        }
        if (finalAction == DecisionAction.ALLOW) {
            reasonCodes.add(ReasonCode.PROJECT_PROVISIONAL_ALLOW);
        }
        switch (applicabilityResult) {
            case APPLICABLE -> reasonCodes.add(ReasonCode.POLICY_APPLICABLE);
            case NOT_APPLICABLE -> reasonCodes.add(ReasonCode.POLICY_NOT_APPLICABLE);
            case CONFLICT -> reasonCodes.add(ReasonCode.POLICY_CONFLICT);
            case INCOMPLETE -> reasonCodes.add(ReasonCode.POLICY_INCOMPLETE);
        }
        return List.copyOf(reasonCodes);
    }
}
