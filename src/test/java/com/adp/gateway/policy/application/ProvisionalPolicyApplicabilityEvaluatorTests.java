package com.adp.gateway.policy.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.List;

import com.adp.gateway.policy.domain.AnalysisStatus;
import com.adp.gateway.policy.domain.ApplicabilityResult;
import com.adp.gateway.policy.domain.ArtifactDigest;
import com.adp.gateway.policy.domain.ArtifactReference;
import com.adp.gateway.policy.domain.PolicyAction;
import com.adp.gateway.policy.domain.PolicyApplicabilitySpec;
import com.adp.gateway.policy.domain.PolicyEvaluation;
import com.adp.gateway.policy.domain.PolicyLifecycleStage;
import com.adp.gateway.policy.domain.PolicySnapshot;
import com.adp.gateway.policy.domain.RuntimeBinding;
import com.adp.gateway.policy.domain.RuntimePolicyContext;
import com.adp.gateway.policy.domain.SourcePolicyEvaluationArtifactRef;
import com.adp.gateway.retrieval.domain.DataClass;
import org.junit.jupiter.api.Test;

class ProvisionalPolicyApplicabilityEvaluatorTests {

    private final ProvisionalPolicyApplicabilityEvaluator evaluator = new ProvisionalPolicyApplicabilityEvaluator();

    @Test
    void returnsApplicableWhenRuntimeBindingMatchesContext() {
        assertThat(evaluator.evaluate(snapshot("mapped", "customer_summary", "CUSTOMER_SUPPORT", "CUSTOMER_IDENTIFIER"),
            context("customer_summary", "CUSTOMER_SUPPORT", List.of("AI_USE"), List.of(DataClass.CUSTOMER_IDENTIFIER))))
            .isEqualTo(ApplicabilityResult.APPLICABLE);
    }

    @Test
    void returnsNotApplicableWhenWorkloadOrPurposeDoesNotMatch() {
        assertThat(evaluator.evaluate(snapshot("mapped", "customer_summary", "CUSTOMER_SUPPORT", "CUSTOMER_IDENTIFIER"),
            context("wallet_screening", "CUSTOMER_SUPPORT", List.of("AI_USE"), List.of(DataClass.CUSTOMER_IDENTIFIER))))
            .isEqualTo(ApplicabilityResult.NOT_APPLICABLE);

        assertThat(evaluator.evaluate(snapshot("mapped", "customer_summary", "CUSTOMER_SUPPORT", "CUSTOMER_IDENTIFIER"),
            context("customer_summary", "MODEL_TRAINING", List.of("AI_USE"), List.of(DataClass.CUSTOMER_IDENTIFIER))))
            .isEqualTo(ApplicabilityResult.NOT_APPLICABLE);
    }

    @Test
    void returnsIncompleteForUnresolvedBindingOrMissingCanonicalRuntimeContext() {
        assertThat(evaluator.evaluate(snapshot("tbd", "customer_summary", "CUSTOMER_SUPPORT", "CUSTOMER_IDENTIFIER"),
            context("customer_summary", "CUSTOMER_SUPPORT", List.of("AI_USE"), List.of(DataClass.CUSTOMER_IDENTIFIER))))
            .isEqualTo(ApplicabilityResult.INCOMPLETE);

        assertThat(evaluator.evaluate(snapshot("mapped", "customer_summary", "CUSTOMER_SUPPORT", "CUSTOMER_IDENTIFIER"),
            incompleteContext()))
            .isEqualTo(ApplicabilityResult.INCOMPLETE);

        assertThat(evaluator.evaluate(snapshot("mapped", "customer_summary", "CUSTOMER_SUPPORT", "CUSTOMER_IDENTIFIER"),
            context("customer_summary", "CUSTOMER_SUPPORT", List.of(), List.of(DataClass.CUSTOMER_IDENTIFIER))))
            .isEqualTo(ApplicabilityResult.INCOMPLETE);
    }

    @Test
    void returnsNotApplicableWhenProcessingContextOrRuntimeDataClassDoesNotMatch() {
        assertThat(evaluator.evaluate(snapshot("mapped", "customer_summary", "CUSTOMER_SUPPORT", "CUSTOMER_IDENTIFIER"),
            context("customer_summary", "CUSTOMER_SUPPORT", List.of("SAAS_CLOUD_USE"), List.of(DataClass.CUSTOMER_IDENTIFIER))))
            .isEqualTo(ApplicabilityResult.NOT_APPLICABLE);

        assertThat(evaluator.evaluate(snapshot("mapped", "customer_summary", "CUSTOMER_SUPPORT", "ACCOUNT_IDENTIFIER"),
            context("customer_summary", "CUSTOMER_SUPPORT", List.of("AI_USE"), List.of(DataClass.CUSTOMER_IDENTIFIER))))
            .isEqualTo(ApplicabilityResult.NOT_APPLICABLE);
    }

    private PolicySnapshot snapshot(
        String mappingStatus,
        String workloadId,
        String purpose,
        String runtimeDataClass
    ) {
        SourcePolicyEvaluationArtifactRef source = new SourcePolicyEvaluationArtifactRef(
            "PEA-1",
            "da-v1",
            new ArtifactDigest("sha256", "da-digest")
        );
        PolicyEvaluation evaluation = new PolicyEvaluation(
            List.of(ref("POL-1", "policy")),
            List.of(ref("RULE-1", "rule")),
            List.of(ref("REQ-1", "requirement")),
            List.of(ref("EV-1", "evidence")),
            PolicyAction.ALLOW,
            List.of(ref("CTRL-1", "control")),
            List.of(ref("VA-1", "validation_artifact")),
            new PolicyApplicabilitySpec(
                AnalysisStatus.VALIDATED,
                AnalysisStatus.VALIDATED,
                "runtime-bound support policy",
                List.of(),
                List.of("AI_USE"),
                List.of("PERSONAL_INFORMATION"),
                new RuntimeBinding(mappingStatus, runtimeDataClass, workloadId, purpose, "BINDING-1")
            )
        );
        return new PolicySnapshot(
            "be-runtime-policy/v1",
            "be-snapshot-digest",
            OffsetDateTime.parse("2026-08-31T00:00:00Z"),
            PolicyLifecycleStage.PROJECT_PROVISIONAL,
            source,
            evaluation
        );
    }

    private RuntimePolicyContext context(
        String workloadId,
        String purpose,
        List<String> processingContexts,
        List<DataClass> runtimeDataClasses
    ) {
        return new RuntimePolicyContext(
            workloadId,
            purpose,
            "customer",
            "subject-digest",
            "canonical-digest",
            runtimeDataClasses,
            processingContexts,
            "internal",
            "runtime-context-digest"
        );
    }

    private RuntimePolicyContext incompleteContext() {
        return new RuntimePolicyContext(
            "customer_summary",
            "CUSTOMER_SUPPORT",
            "customer",
            "subject-digest",
            null,
            List.of(),
            List.of(),
            "internal",
            "runtime-context-digest"
        );
    }

    private ArtifactReference ref(String refId, String refType) {
        return new ArtifactReference(refId, refType, "v1");
    }
}
