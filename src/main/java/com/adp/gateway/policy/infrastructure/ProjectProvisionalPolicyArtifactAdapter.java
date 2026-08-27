package com.adp.gateway.policy.infrastructure;

import java.util.List;

import com.adp.gateway.policy.domain.PolicyArtifact;
import com.adp.gateway.policy.domain.PolicyArtifactPort;
import org.springframework.stereotype.Component;

@Component
public class ProjectProvisionalPolicyArtifactAdapter implements PolicyArtifactPort {

    @Override
    public PolicyArtifact load(String workloadId) {
        return new PolicyArtifact(
            "PROJECT_PROVISIONAL",
            "0.0.0",
            "candidate",
            workloadId,
            List.of("BE-0 fixture. Not approved for production policy enforcement."),
            "local-fixture"
        );
    }
}
