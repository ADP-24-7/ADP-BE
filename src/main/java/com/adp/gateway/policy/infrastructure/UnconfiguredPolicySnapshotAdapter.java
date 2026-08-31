package com.adp.gateway.policy.infrastructure;

import java.time.OffsetDateTime;
import java.util.List;

import com.adp.gateway.policy.domain.AnalysisStatus;
import com.adp.gateway.policy.domain.ArtifactDigest;
import com.adp.gateway.policy.domain.ArtifactReference;
import com.adp.gateway.policy.domain.PolicyAction;
import com.adp.gateway.policy.domain.PolicyApplicabilitySpec;
import com.adp.gateway.policy.domain.PolicyEvaluation;
import com.adp.gateway.policy.domain.PolicyLifecycleStage;
import com.adp.gateway.policy.domain.PolicySelectionContext;
import com.adp.gateway.policy.domain.PolicySnapshot;
import com.adp.gateway.policy.domain.PolicySnapshotPort;
import com.adp.gateway.policy.domain.RuntimeBinding;
import com.adp.gateway.policy.domain.SourcePolicyEvaluationArtifactRef;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "adp.mock-runtime.enabled", havingValue = "false", matchIfMissing = true)
public class UnconfiguredPolicySnapshotAdapter implements PolicySnapshotPort {

    private static final OffsetDateTime EFFECTIVE_AT = OffsetDateTime.parse("2026-01-01T00:00:00Z");

    @Override
    public PolicySnapshot load(PolicySelectionContext context) {
        PolicyEvaluation evaluation = new PolicyEvaluation(
            List.of(),
            List.of(new ArtifactReference("NO_POLICY_SOURCE_CONFIGURED", "rule", "0.0.0")),
            List.of(),
            List.of(),
            PolicyAction.REVIEW,
            List.of(new ArtifactReference("POLICY_SNAPSHOT_SELECTION", "control", "0.0.0")),
            List.of(),
            new PolicyApplicabilitySpec(
                AnalysisStatus.CANDIDATE,
                AnalysisStatus.CANDIDATE,
                "No policy snapshot source is configured.",
                List.of("Runtime execution is available, but no policy source adapter matched this environment."),
                List.of(),
                List.of(),
                new RuntimeBinding(
                    "unmapped",
                    "UNKNOWN",
                    context.workloadId(),
                    context.purposeCode(),
                    "NO_POLICY_SOURCE_CONFIGURED"
                )
            )
        );
        return new PolicySnapshot(
            "be-runtime-policy/unconfigured/0.0.0",
            "be-snapshot-unconfigured:" + context.workloadId() + ":" + context.purposeCode() + ":"
                + context.providerProfileId(),
            EFFECTIVE_AT,
            PolicyLifecycleStage.PROJECT_PROVISIONAL,
            new SourcePolicyEvaluationArtifactRef(
                "NO_POLICY_SOURCE_CONFIGURED",
                "0.0.0",
                new ArtifactDigest("sha256", "no-policy-source-configured")
            ),
            evaluation
        );
    }
}
