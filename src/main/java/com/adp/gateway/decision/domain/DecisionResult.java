package com.adp.gateway.decision.domain;

import com.adp.gateway.common.error.ReasonCode;

public record DecisionResult(
    String decisionId,
    String outcome,
    ReasonCode reasonCode,
    String policyArtifactId
) {
}
