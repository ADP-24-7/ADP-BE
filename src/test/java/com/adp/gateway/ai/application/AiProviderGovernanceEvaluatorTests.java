package com.adp.gateway.ai.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.adp.gateway.ai.domain.AiProviderGovernanceContract;
import org.junit.jupiter.api.Test;

class AiProviderGovernanceEvaluatorTests {

    private final AiProviderGovernanceEvaluator evaluator = new AiProviderGovernanceEvaluator();

    @Test
    void approvedProviderModelWorkloadRegionRetentionAndExecutionOnlyReusePass() {
        var result = evaluate("provider", "model", "customer_summary", "KR", "KR",
            "SESSION_ONLY", 0, "VERIFIED", List.of("REQUEST_EXECUTION"), "destination-digest");

        assertThat(result.isPassed()).isTrue();
        assertThat(result.reasonCodes()).isEmpty();
    }

    @Test
    void providerModelAndWorkloadMismatchesFailClosed() {
        assertThat(evaluate("unknown", "model", "customer_summary", "KR", "KR",
            "SESSION_ONLY", 0, "VERIFIED", List.of("REQUEST_EXECUTION"), "destination-digest").reasonCodes())
            .contains("AI_PROVIDER_NOT_APPROVED");
        assertThat(evaluate("provider", "unknown", "customer_summary", "KR", "KR",
            "SESSION_ONLY", 0, "VERIFIED", List.of("REQUEST_EXECUTION"), "destination-digest").reasonCodes())
            .contains("AI_MODEL_NOT_APPROVED");
        assertThat(evaluate("provider", "model", "wrong", "KR", "KR",
            "SESSION_ONLY", 0, "VERIFIED", List.of("REQUEST_EXECUTION"), "destination-digest").reasonCodes())
            .contains("AI_WORKLOAD_OR_DESTINATION_BINDING_MISMATCH");
        assertThat(evaluator.evaluate(contract(), "provider", "model", "customer_summary", "CUSTOMER_SUPPORT",
            null, null, "KR", "KR", "SESSION_ONLY", 0, "VERIFIED", List.of("REQUEST_EXECUTION")).reasonCodes())
            .contains("AI_WORKLOAD_OR_DESTINATION_BINDING_MISMATCH");
    }

    @Test
    void missingDisallowedAndMismatchedRegionsBlock() {
        assertThat(evaluate("provider", "model", "customer_summary", "KR", "UNRESOLVED",
            "SESSION_ONLY", 0, "VERIFIED", List.of("REQUEST_EXECUTION"), "destination-digest").reasonCodes())
            .contains("PROVIDER_REGION_REQUIRED");
        assertThat(evaluate("provider", "model", "customer_summary", "US", "US",
            "SESSION_ONLY", 0, "VERIFIED", List.of("REQUEST_EXECUTION"), "destination-digest").reasonCodes())
            .contains("PROVIDER_REGION_NOT_ALLOWED");
        assertThat(evaluate("provider", "model", "customer_summary", "KR", "US",
            "SESSION_ONLY", 0, "VERIFIED", List.of("REQUEST_EXECUTION"), "destination-digest").reasonCodes())
            .contains("PROVIDER_REGION_MISMATCH");
    }

    @Test
    void unverifiedMismatchedAndExcessiveRetentionBlock() {
        assertThat(evaluate("provider", "model", "customer_summary", "KR", "KR",
            "SESSION_ONLY", 0, "UNVERIFIED", List.of("REQUEST_EXECUTION"), "destination-digest").reasonCodes())
            .contains("RETENTION_UNVERIFIED");
        assertThat(evaluate("provider", "model", "customer_summary", "KR", "KR",
            "BOUNDED_RETENTION", 0, "VERIFIED", List.of("REQUEST_EXECUTION"), "destination-digest").reasonCodes())
            .contains("RETENTION_POLICY_MISMATCH");
        assertThat(evaluate("provider", "model", "customer_summary", "KR", "KR",
            "SESSION_ONLY", 1, "VERIFIED", List.of("REQUEST_EXECUTION"), "destination-digest").reasonCodes())
            .contains("RETENTION_LIMIT_EXCEEDED");
    }

    @Test
    void trainingAndSecondaryReuseBlock() {
        assertThat(evaluate("provider", "model", "customer_summary", "KR", "KR",
            "SESSION_ONLY", 0, "VERIFIED", List.of("REQUEST_EXECUTION", "MODEL_TRAINING"), "destination-digest").reasonCodes())
            .contains("MODEL_TRAINING_NOT_ALLOWED");
        assertThat(evaluate("provider", "model", "customer_summary", "KR", "KR",
            "SESSION_ONLY", 0, "VERIFIED", List.of("REQUEST_EXECUTION", "SECONDARY_ANALYTICS"), "destination-digest").reasonCodes())
            .contains("REUSE_SCOPE_MISMATCH");
        assertThat(evaluate("provider", "model", "customer_summary", "KR", "KR",
            "SESSION_ONLY", 0, "VERIFIED", null, "destination-digest").reasonCodes())
            .contains("REUSE_SCOPE_MISMATCH");
    }

    @Test
    void staleDestinationDigestAndCompoundFailuresRemainVisible() {
        var result = evaluate("provider", "model", "customer_summary", "KR", "US",
            "UNSPECIFIED", null, "UNVERIFIED", List.of("AI_MODEL_IMPROVEMENT"), "stale");

        assertThat(result.reasonCodes()).contains(
            "AI_WORKLOAD_OR_DESTINATION_BINDING_MISMATCH",
            "PROVIDER_REGION_MISMATCH",
            "RETENTION_UNVERIFIED",
            "MODEL_TRAINING_NOT_ALLOWED"
        );
    }

    private com.adp.gateway.ai.domain.AiProviderGovernanceDecision evaluate(
        String provider,
        String model,
        String workload,
        String requestedRegion,
        String resolvedRegion,
        String retentionMode,
        Integer retentionDays,
        String retentionVerification,
        List<String> reuse,
        String destinationDigest
    ) {
        return evaluator.evaluate(contract(), provider, model, workload, "CUSTOMER_SUPPORT", "destination",
            destinationDigest, requestedRegion, resolvedRegion, retentionMode, retentionDays,
            retentionVerification, reuse);
    }

    private AiProviderGovernanceContract contract() {
        return new AiProviderGovernanceContract(
            "e2-provider-governance/1.2.0", "governance-digest", "ACTIVE_FAIL_CLOSED",
            "provider", List.of("model"), "customer_summary", "CUSTOMER_SUPPORT",
            List.of("destination"), List.of("destination-digest"), List.of("KR"), true,
            "SESSION_ONLY", 0, List.of("REQUEST_EXECUTION")
        );
    }
}
