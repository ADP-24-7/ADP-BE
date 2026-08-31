package com.adp.gateway.policy.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

class PolicyEvaluationHandoffNormalizerTests {

    private final PolicyEvaluationHandoffNormalizer normalizer =
        new PolicyEvaluationHandoffNormalizer(new PolicyEvaluationHandoffValidator());

    @Test
    void preservesDaHandoffDispositionSeparatelyFromBeRuntimePolicyAction() {
        NormalizedPolicyEvaluation normalized = normalizer.normalize(artifact());

        assertThat(normalized.handoffDisposition()).isEqualTo("candidate_handoff");
        assertThat(normalized.evaluation().policyAction().name()).isEqualTo("REVIEW");
        assertThat(normalized.sourcePolicyEvaluationArtifactRef().artifactId()).isEqualTo("PEA-1");
        assertThat(normalized.sourcePolicyEvaluationArtifactRef().artifactVersion()).isEqualTo("1.0.0");
        assertThat(normalized.sourcePolicyEvaluationArtifactRef().artifactDigest().value()).isEqualTo("digest-1");
        assertThat(normalized.evaluation().matchedRuleRefs().getFirst().refType()).isEqualTo("rule");
        assertThat(normalized.evaluation().applicabilitySpec().processingContexts()).containsExactly("AI_USE");
        assertThat(normalized.evaluation().applicabilitySpec().runtimeBinding().workloadId()).isEqualTo("customer_summary");
    }

    @Test
    void rejectsUnsupportedSchemaVersionBeforeNormalization() {
        PolicyEvaluationHandoffArtifact unsupported = new PolicyEvaluationHandoffArtifact(
            "v2",
            "PEA-1",
            "1.0.0",
            "validated",
            "candidate_handoff",
            List.of(ref("POL-1", "policy")),
            List.of(ref("RULE-1", "rule")),
            List.of(ref("REQ-1", "requirement")),
            List.of(ref("EV-1", "evidence")),
            List.of(ref("CTRL-1", "control")),
            List.of(ref("VA-1", "validation_artifact")),
            new HandoffApplicability("validated", "runtime-bound", List.of()),
            List.of("AI_USE"),
            List.of("PERSONAL_INFORMATION"),
            new HandoffRuntimeBinding("mapped", "CUSTOMER_IDENTIFIER", "customer_summary", "CUSTOMER_SUPPORT", "BINDING-1"),
            new HandoffDigest("sha256", "digest-1")
        );

        assertThatThrownBy(() -> normalizer.normalize(unsupported))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("unsupported schema_version");
    }

    @Test
    void validatorRejectsDigestMismatch() {
        PolicyEvaluationHandoffValidator validator = new PolicyEvaluationHandoffValidator();

        assertThatThrownBy(() -> validator.validateDigest(artifact(), "{\"artifact_id\":\"PEA-1\"}"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("digest mismatch");
    }

    private PolicyEvaluationHandoffArtifact artifact() {
        return new PolicyEvaluationHandoffArtifact(
            "v1",
            "PEA-1",
            "1.0.0",
            "validated",
            "candidate_handoff",
            List.of(ref("POL-1", "policy")),
            List.of(ref("RULE-1", "rule")),
            List.of(ref("REQ-1", "requirement")),
            List.of(ref("EV-1", "evidence")),
            List.of(ref("CTRL-1", "control")),
            List.of(ref("VA-1", "validation_artifact")),
            new HandoffApplicability("validated", "runtime-bound", List.of()),
            List.of("AI_USE"),
            List.of("PERSONAL_INFORMATION"),
            new HandoffRuntimeBinding("mapped", "CUSTOMER_IDENTIFIER", "customer_summary", "CUSTOMER_SUPPORT", "BINDING-1"),
            new HandoffDigest("sha256", "digest-1")
        );
    }

    private HandoffReference ref(String refId, String refType) {
        return new HandoffReference(refId, refType, "v1");
    }
}
