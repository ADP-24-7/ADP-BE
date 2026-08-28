package com.adp.gateway.policy.infrastructure;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;

import com.adp.gateway.decision.domain.DecisionAction;
import com.adp.gateway.policy.domain.PolicyArtifact;
import com.adp.gateway.policy.domain.PolicyEvaluation;
import com.adp.gateway.policy.domain.PolicyLifecycleStage;
import com.adp.gateway.policy.domain.PolicySnapshot;
import com.adp.gateway.policy.domain.PolicySnapshotPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "adp.mock-runtime.enabled", havingValue = "true")
public class ProjectProvisionalPolicySnapshotAdapter implements PolicySnapshotPort {

    private final Clock clock;

    public ProjectProvisionalPolicySnapshotAdapter(Clock clock) {
        this.clock = clock;
    }

    @Override
    public PolicySnapshot load(String workloadId) {
        PolicyArtifact sourceArtifact = new PolicyArtifact(
            "PROJECT_PROVISIONAL",
            "0.0.0",
            PolicyLifecycleStage.PROJECT_PROVISIONAL,
            workloadId,
            List.of("BE-4 fixture. Not approved for production policy enforcement."),
            "local-fixture"
        );
        PolicyEvaluation evaluation = new PolicyEvaluation(
            List.of("PROJECT_PROVISIONAL_RULE"),
            List.of(),
            DecisionAction.ALLOW,
            List.of("RUNTIME_AUTHORIZATION", "SUBJECT_SCOPE"),
            sourceArtifact.artifactVersion(),
            sourceArtifact.digest()
        );

        return new PolicySnapshot(
            sourceArtifact.artifactVersion(),
            sourceArtifact.digest(),
            OffsetDateTime.now(clock),
            PolicyLifecycleStage.PROJECT_PROVISIONAL,
            sourceArtifact,
            evaluation
        );
    }
}
