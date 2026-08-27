package com.adp.gateway.decision.application;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import com.adp.gateway.common.contract.RuntimeRequestContext;
import com.adp.gateway.common.error.ReasonCode;
import com.adp.gateway.decision.domain.DecisionResult;
import com.adp.gateway.policy.domain.PolicyArtifact;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "adp.mock-runtime.enabled", havingValue = "true")
public class FakeDecisionService {

    public DecisionResult evaluate(RuntimeRequestContext context, PolicyArtifact policyArtifact) {
        String decisionIdentity = String.join(
            ":",
            context.requestId(),
            policyArtifact.artifactId(),
            policyArtifact.artifactVersion(),
            policyArtifact.digest()
        );

        return new DecisionResult(
            "dec_" + UUID.nameUUIDFromBytes(decisionIdentity.getBytes(StandardCharsets.UTF_8)),
            "ALLOW",
            ReasonCode.MOCK_DECISION_ALLOW,
            policyArtifact.artifactId(),
            policyArtifact.artifactVersion(),
            policyArtifact.digest()
        );
    }
}
