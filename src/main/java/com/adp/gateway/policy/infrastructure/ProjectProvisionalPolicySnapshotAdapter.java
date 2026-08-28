package com.adp.gateway.policy.infrastructure;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;

import com.adp.gateway.policy.domain.ArtifactDigest;
import com.adp.gateway.policy.domain.ArtifactReference;
import com.adp.gateway.policy.domain.PolicyAction;
import com.adp.gateway.policy.domain.PolicyEvaluation;
import com.adp.gateway.policy.domain.PolicyLifecycleStage;
import com.adp.gateway.policy.domain.PolicySnapshot;
import com.adp.gateway.policy.domain.PolicySnapshotPort;
import com.adp.gateway.policy.domain.SourcePolicyEvaluationArtifactRef;
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
        SourcePolicyEvaluationArtifactRef sourceArtifact = new SourcePolicyEvaluationArtifactRef(
            "PROJECT_PROVISIONAL_POLICY_EVALUATION",
            "0.0.0",
            new ArtifactDigest("sha256", "local-fixture-policy-evaluation")
        );
        PolicyEvaluation evaluation = new PolicyEvaluation(
            List.of(new ArtifactReference("PROJECT_PROVISIONAL_POLICY", "policy", "0.0.0")),
            List.of(new ArtifactReference("PROJECT_PROVISIONAL_RULE", "rule", "0.0.0")),
            List.of(new ArtifactReference("PROJECT_PROVISIONAL_REQUIREMENT", "requirement", "0.0.0")),
            List.of(),
            PolicyAction.ALLOW,
            List.of(
                new ArtifactReference("RUNTIME_AUTHORIZATION", "control", "0.0.0"),
                new ArtifactReference("SUBJECT_SCOPE", "control", "0.0.0")
            ),
            List.of()
        );

        return new PolicySnapshot(
            "be-runtime-policy/0.0.0",
            "be-snapshot-local-fixture",
            OffsetDateTime.now(clock),
            PolicyLifecycleStage.PROJECT_PROVISIONAL,
            sourceArtifact,
            evaluation
        );
    }
}
