package com.adp.gateway.decision.application;

import java.time.OffsetDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;

import com.adp.gateway.common.error.ReasonCode;
import com.adp.gateway.context.application.CanonicalValueHasher;
import com.adp.gateway.context.domain.CanonicalContext;
import com.adp.gateway.decision.domain.ExecutionPackPolicyEvaluation;
import com.adp.gateway.decision.domain.RuntimeDecision;
import com.adp.gateway.decision.domain.FinalAction;
import com.adp.gateway.egress.domain.DestinationProfile;
import com.adp.gateway.egress.domain.ExecutionPackType;
import org.springframework.stereotype.Component;

@Component
public class ExecutionPackPolicyGateResolver {

    private final Map<ExecutionPackType, ExecutionPackPolicyGate> gates;
    private final CanonicalValueHasher hasher;
    private static final Set<ExecutionPackType> REQUIRED_GATES = Set.of(ExecutionPackType.DIGITAL_ASSET);

    public ExecutionPackPolicyGateResolver(List<ExecutionPackPolicyGate> gates, CanonicalValueHasher hasher) {
        Map<ExecutionPackType, ExecutionPackPolicyGate> indexed = new EnumMap<>(ExecutionPackType.class);
        gates.forEach(gate -> {
            ExecutionPackPolicyGate previous = indexed.putIfAbsent(gate.supportedPack(), gate);
            if (previous != null) {
                throw new IllegalStateException("Duplicate policy gate for pack " + gate.supportedPack());
            }
        });
        this.gates = Map.copyOf(indexed);
        this.hasher = hasher;
    }

    public Optional<ExecutionPackPolicyEvaluation> evaluate(
        ExecutionPackType packType,
        CanonicalContext context,
        DestinationProfile destinationProfile,
        RuntimeDecision baselineDecision,
        OffsetDateTime requestStartedAt
    ) {
        ExecutionPackPolicyGate gate = gates.get(packType);
        if (gate != null) {
            return Optional.of(gate.evaluate(context, destinationProfile, baselineDecision, requestStartedAt));
        }
        if (!REQUIRED_GATES.contains(packType)) {
            return Optional.empty();
        }
        ReasonCode reason = ReasonCode.EXECUTION_PACK_POLICY_GATE_NOT_CONFIGURED;
        String profileDigest = hasher.hash("execution-pack-policy-gate-not-configured|" + packType.name());
        String decisionIdentity = String.join("|",
            baselineDecision.decisionId(), profileDigest, FinalAction.BLOCK.name(), reason.name()
        );
        RuntimeDecision blocked = new RuntimeDecision(
            "dec_" + UUID.nameUUIDFromBytes(decisionIdentity.getBytes(StandardCharsets.UTF_8)),
            baselineDecision.policyAction(), FinalAction.BLOCK,
            appendReason(baselineDecision, reason), baselineDecision.authorizationResult(),
            baselineDecision.applicabilityResult(), baselineDecision.matchedPolicyRefs(),
            baselineDecision.matchedRuleRefs(), baselineDecision.requirementRefs(),
            baselineDecision.evidenceRefs(), baselineDecision.requiredControls(),
            baselineDecision.validationArtifactRefs(), baselineDecision.policyVersion(),
            baselineDecision.snapshotDigest(), baselineDecision.runtimeContextDigest(),
            baselineDecision.sourcePolicyEvaluationArtifactRef()
        );
        return Optional.of(new ExecutionPackPolicyEvaluation(
            packType, "NOT_CONFIGURED", "0", profileDigest,
            baselineDecision.finalAction(), FinalAction.BLOCK, FinalAction.BLOCK,
            List.of(reason), null, null, null, blocked
        ));
    }

    private List<ReasonCode> appendReason(RuntimeDecision decision, ReasonCode reason) {
        java.util.ArrayList<ReasonCode> reasons = new java.util.ArrayList<>(decision.runtimeReasonCodes());
        if (!reasons.contains(reason)) {
            reasons.add(reason);
        }
        return List.copyOf(reasons);
    }
}
