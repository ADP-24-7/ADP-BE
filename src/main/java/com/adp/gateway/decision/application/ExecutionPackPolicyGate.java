package com.adp.gateway.decision.application;

import java.time.OffsetDateTime;

import com.adp.gateway.context.domain.CanonicalContext;
import com.adp.gateway.decision.domain.ExecutionPackPolicyEvaluation;
import com.adp.gateway.decision.domain.RuntimeDecision;
import com.adp.gateway.egress.domain.DestinationProfile;
import com.adp.gateway.egress.domain.ExecutionPackType;

public interface ExecutionPackPolicyGate {

    ExecutionPackType supportedPack();

    ExecutionPackPolicyEvaluation evaluate(
        CanonicalContext context,
        DestinationProfile destinationProfile,
        RuntimeDecision baselineDecision,
        OffsetDateTime requestStartedAt
    );
}
