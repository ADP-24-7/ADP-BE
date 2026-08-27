package com.adp.gateway.decision.application;

import java.util.UUID;

import com.adp.gateway.common.contract.RuntimeRequestContext;
import com.adp.gateway.common.error.ReasonCode;
import com.adp.gateway.decision.domain.DecisionResult;
import com.adp.gateway.policy.domain.PolicyArtifact;
import org.springframework.stereotype.Service;

@Service
public class FakeDecisionService {

    public DecisionResult evaluate(RuntimeRequestContext context, PolicyArtifact policyArtifact) {
        return new DecisionResult(
            "dec_" + UUID.nameUUIDFromBytes((context.requestId() + policyArtifact.artifactId()).getBytes()),
            "ALLOW",
            ReasonCode.MOCK_DECISION_ALLOW,
            policyArtifact.artifactId(),
            policyArtifact.artifactVersion(),
            policyArtifact.digest()
        );
    }
}
