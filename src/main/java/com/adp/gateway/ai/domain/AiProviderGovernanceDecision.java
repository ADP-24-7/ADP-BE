package com.adp.gateway.ai.domain;

import java.util.List;

public record AiProviderGovernanceDecision(
    String decision,
    String providerDecision,
    String modelDecision,
    String workloadDecision,
    String regionDecision,
    String regionReasonCode,
    String retentionDecision,
    String retentionReasonCode,
    String reuseDecision,
    String reuseReasonCode,
    List<String> reasonCodes
) {
    public AiProviderGovernanceDecision {
        reasonCodes = List.copyOf(reasonCodes);
    }

    public boolean isPassed() {
        return "PASS".equals(decision);
    }
}
