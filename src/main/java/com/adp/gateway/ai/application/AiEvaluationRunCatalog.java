package com.adp.gateway.ai.application;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.adp.gateway.ai.domain.AiEvaluationRunDefinition;
import org.springframework.stereotype.Component;

@Component
public class AiEvaluationRunCatalog {
    public static final String BASELINE_RUN_ID = "ai-eval-baseline-2026-09-07";
    public static final String BASELINE_CASE_ID = "customer-summary-ko-001";

    private final Map<String, AiEvaluationRunDefinition> runs;

    public AiEvaluationRunCatalog(AiModelProfileCatalog modelProfiles) {
        var baseline = new AiEvaluationRunDefinition(
            BASELINE_RUN_ID,
            "1.0.0",
            "financial_synthetic",
            "financial_synthetic_processed_v1",
            "sha256:9afdc4bf89c0047a5e90f21e6f8eaffb4f6c148998f1740f30baf666bdae0a44",
            modelProfiles.policySnapshotDigest(),
            Set.of(BASELINE_CASE_ID),
            modelProfiles.profiles().stream().map(profile -> profile.profileId()).collect(java.util.stream.Collectors.toSet())
        );
        this.runs = Map.of(baseline.evaluationRunId(), baseline);
    }

    public Optional<AiEvaluationRunDefinition> find(String evaluationRunId) {
        return evaluationRunId == null ? Optional.empty() : Optional.ofNullable(runs.get(evaluationRunId));
    }
}
