package com.adp.gateway.decision.domain;

import java.util.List;

import com.adp.gateway.common.error.ReasonCode;
import com.adp.gateway.egress.domain.ExecutionPackType;

public record ExecutionPackPolicyEvaluation(
    ExecutionPackType packType,
    String profileId,
    String profileVersion,
    String profileDigest,
    String result,
    List<ReasonCode> reasonCodes,
    RuntimeDecision decision
) {
    public ExecutionPackPolicyEvaluation {
        reasonCodes = List.copyOf(reasonCodes);
    }
}
