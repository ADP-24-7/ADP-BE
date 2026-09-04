package com.adp.gateway.decision.application;

import java.time.OffsetDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.adp.gateway.context.domain.CanonicalContext;
import com.adp.gateway.decision.domain.ExecutionPackPolicyEvaluation;
import com.adp.gateway.decision.domain.RuntimeDecision;
import com.adp.gateway.egress.domain.DestinationProfile;
import com.adp.gateway.egress.domain.ExecutionPackType;
import org.springframework.stereotype.Component;

@Component
public class ExecutionPackPolicyGateResolver {

    private final Map<ExecutionPackType, ExecutionPackPolicyGate> gates;

    public ExecutionPackPolicyGateResolver(List<ExecutionPackPolicyGate> gates) {
        Map<ExecutionPackType, ExecutionPackPolicyGate> indexed = new EnumMap<>(ExecutionPackType.class);
        gates.forEach(gate -> {
            ExecutionPackPolicyGate previous = indexed.putIfAbsent(gate.supportedPack(), gate);
            if (previous != null) {
                throw new IllegalStateException("Duplicate policy gate for pack " + gate.supportedPack());
            }
        });
        this.gates = Map.copyOf(indexed);
    }

    public Optional<ExecutionPackPolicyEvaluation> evaluate(
        ExecutionPackType packType,
        CanonicalContext context,
        DestinationProfile destinationProfile,
        RuntimeDecision baselineDecision,
        OffsetDateTime requestStartedAt
    ) {
        return Optional.ofNullable(gates.get(packType))
            .map(gate -> gate.evaluate(context, destinationProfile, baselineDecision, requestStartedAt));
    }
}
