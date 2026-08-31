package com.adp.gateway.policy.infrastructure;

import java.time.OffsetDateTime;
import java.util.List;

import com.adp.gateway.policy.domain.ArtifactDigest;
import com.adp.gateway.policy.domain.ArtifactReference;
import com.adp.gateway.policy.domain.AnalysisStatus;
import com.adp.gateway.policy.domain.PolicyApplicabilitySpec;
import com.adp.gateway.policy.domain.PolicyAction;
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
@ConditionalOnProperty(name = "adp.mock-runtime.enabled", havingValue = "true")
public class ProjectProvisionalPolicySnapshotAdapter implements PolicySnapshotPort {

    private static final String FIXTURE_WORKLOAD_ID = "customer_summary";
    private static final String FIXTURE_PURPOSE = "CUSTOMER_SUPPORT";
    private static final String FIXTURE_PROVIDER = "internal-provider";
    private static final String LEGACY_FIXTURE_WORKLOAD_ID = "workload_be0";
    private static final String LEGACY_FIXTURE_PURPOSE = "BE-0 local E2E";
    private static final OffsetDateTime FIXTURE_EFFECTIVE_AT = OffsetDateTime.parse("2026-01-01T00:00:00Z");

    @Override
    public PolicySnapshot load(PolicySelectionContext context) {
        if (matchesRuntimeFixture(context)) {
            return allowSnapshot(
                FIXTURE_WORKLOAD_ID,
                FIXTURE_PURPOSE,
                "be-snapshot-local-fixture:customer-summary:customer-support:internal-provider"
            );
        }
        if (matchesLegacyFixture(context)) {
            return allowSnapshot(
                LEGACY_FIXTURE_WORKLOAD_ID,
                LEGACY_FIXTURE_PURPOSE,
                "be-snapshot-local-fixture:workload-be0:be0-local-e2e:no-provider"
            );
        }
        return noPolicySnapshot(context);
    }

    private boolean matchesRuntimeFixture(PolicySelectionContext context) {
        return FIXTURE_WORKLOAD_ID.equals(context.workloadId())
            && FIXTURE_PURPOSE.equals(context.purposeCode())
            && FIXTURE_PROVIDER.equals(context.providerProfileId());
    }

    private boolean matchesLegacyFixture(PolicySelectionContext context) {
        return LEGACY_FIXTURE_WORKLOAD_ID.equals(context.workloadId())
            && LEGACY_FIXTURE_PURPOSE.equals(context.purposeCode())
            && context.providerProfileId() == null;
    }

    private PolicySnapshot allowSnapshot(String workloadId, String purpose, String snapshotDigest) {
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
            List.of(),
            new PolicyApplicabilitySpec(
                AnalysisStatus.VALIDATED,
                AnalysisStatus.VALIDATED,
                "PROJECT_PROVISIONAL local runtime fixture",
                List.of("Not approved for production policy enforcement."),
                List.of("AI_USE"),
                List.of("PERSONAL_INFORMATION"),
                new RuntimeBinding(
                    "mapped",
                    "CUSTOMER_IDENTIFIER",
                    workloadId,
                    purpose,
                    "PROJECT_PROVISIONAL_BINDING"
                )
            )
        );

        return new PolicySnapshot(
            "be-runtime-policy/0.0.0",
            snapshotDigest,
            FIXTURE_EFFECTIVE_AT,
            PolicyLifecycleStage.PROJECT_PROVISIONAL,
            sourceArtifact,
            evaluation
        );
    }

    private PolicySnapshot noPolicySnapshot(PolicySelectionContext context) {
        SourcePolicyEvaluationArtifactRef sourceArtifact = new SourcePolicyEvaluationArtifactRef(
            "PROJECT_PROVISIONAL_NO_POLICY",
            "0.0.0",
            new ArtifactDigest("sha256", "local-fixture-no-policy")
        );
        PolicyEvaluation evaluation = new PolicyEvaluation(
            List.of(),
            List.of(new ArtifactReference("PROJECT_PROVISIONAL_NO_POLICY_RULE", "rule", "0.0.0")),
            List.of(),
            List.of(),
            PolicyAction.REVIEW,
            List.of(new ArtifactReference("POLICY_SNAPSHOT_SELECTION", "control", "0.0.0")),
            List.of(),
            new PolicyApplicabilitySpec(
                AnalysisStatus.CANDIDATE,
                AnalysisStatus.CANDIDATE,
                "No PROJECT_PROVISIONAL policy fixture matched the requested scope.",
                List.of("Policy selection returned no validated runtime binding for this scope."),
                List.of(),
                List.of(),
                new RuntimeBinding(
                    "unmapped",
                    "UNKNOWN",
                    context.workloadId(),
                    context.purposeCode(),
                    "PROJECT_PROVISIONAL_NO_BINDING"
                )
            )
        );
        return new PolicySnapshot(
            "be-runtime-policy/no-policy/0.0.0",
            "be-snapshot-local-fixture:no-policy:"
                + context.workloadId() + ":" + context.purposeCode() + ":" + context.providerProfileId(),
            FIXTURE_EFFECTIVE_AT,
            PolicyLifecycleStage.PROJECT_PROVISIONAL,
            sourceArtifact,
            evaluation
        );
    }
}
