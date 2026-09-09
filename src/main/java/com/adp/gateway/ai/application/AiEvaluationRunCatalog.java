package com.adp.gateway.ai.application;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.List;

import com.adp.gateway.ai.domain.AiEvaluationCaseDefinition;
import com.adp.gateway.ai.domain.AiEvaluationReference;
import com.adp.gateway.ai.domain.AiEvaluationRunDefinition;
import com.adp.gateway.context.application.CanonicalValueHasher;
import com.adp.gateway.runtime.application.RuntimeInputHasher;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.stereotype.Component;

@Component
public class AiEvaluationRunCatalog {
    public static final String BASELINE_RUN_ID = "ai-eval-baseline-2026-09-07";
    public static final String BASELINE_CASE_ID = "customer-summary-ko-001";
    public static final String DA_PROVENANCE_RUN_ID = "ai-eval-da-provenance-2026-09-10-r2";
    public static final String DA_PROVENANCE_CASE_ID = "customer-summary-da-10832-001";
    public static final String DA_PROVENANCE_DATASET_ROW_REF =
        "financial_synthetic_processed_v1:customers.csv#CustomerID=10832:"
            + "sha256:18f0831cf7e970ad9d8c376d3a3877c86612270dd259745b8a14225761de5dfe";

    private final Map<String, AiEvaluationRunDefinition> runs;

    private final RuntimeInputHasher inputHasher;

    public AiEvaluationRunCatalog(
        AiModelProfileCatalog modelProfiles,
        RuntimeInputHasher inputHasher,
        ObjectMapper objectMapper,
        CanonicalValueHasher hasher
    ) {
        this.inputHasher = inputHasher;
        String expectedInputDigest = inputHasher.hash(Map.of(
            "prompt", AiEvaluationPrompt.TEXT
        ));
        var evaluationCase = new AiEvaluationCaseDefinition(
            BASELINE_CASE_ID, "synthetic:customer-100", "ai-evaluation-input/v1", expectedInputDigest
        );
        var profileBindings = modelProfiles.profiles().stream()
            .map(profile -> Map.of(
                "profileId", profile.profileId(),
                "profileDigest", profile.modelProfileDigest(),
                "destinationDigest", profile.destinationProfileDigest()
            ))
            .toList();
        String contractDigest = digest(
            objectMapper.copy().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true),
            hasher,
            Map.of(
                "runId", BASELINE_RUN_ID,
                "runVersion", "1.0.0",
                "datasetId", "financial_synthetic",
                "datasetVersion", "financial_synthetic_processed_v1",
                "datasetDigest", "sha256:9afdc4bf89c0047a5e90f21e6f8eaffb4f6c148998f1740f30baf666bdae0a44",
                "policySnapshotDigest", modelProfiles.policySnapshotDigest(),
                "cases", List.of(Map.of(
                    "caseId", evaluationCase.caseId(),
                    "datasetRowRef", evaluationCase.datasetRowRef(),
                    "inputSchemaVersion", evaluationCase.inputSchemaVersion(),
                    "expectedInputDigest", evaluationCase.expectedInputDigest()
                )),
                "modelBindings", profileBindings
            )
        );
        var baseline = runDefinition(
            BASELINE_RUN_ID,
            "1.0.0",
            "financial_synthetic",
            "financial_synthetic_processed_v1",
            "sha256:9afdc4bf89c0047a5e90f21e6f8eaffb4f6c148998f1740f30baf666bdae0a44",
            modelProfiles.policySnapshotDigest(),
            contractDigest,
            evaluationCase,
            modelProfiles.profiles().stream().map(profile -> profile.profileId()).collect(java.util.stream.Collectors.toSet())
        );
        var daCase = new AiEvaluationCaseDefinition(
            DA_PROVENANCE_CASE_ID,
            DA_PROVENANCE_DATASET_ROW_REF,
            "ai-evaluation-input/v1",
            expectedInputDigest
        );
        String daContractDigest = digest(
            objectMapper.copy().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true),
            hasher,
            Map.of(
                "runId", DA_PROVENANCE_RUN_ID,
                "runVersion", "1.0.0",
                "datasetId", "financial_synthetic",
                "datasetVersion", "financial_synthetic_processed_v1",
                "datasetDigest", "sha256:9afdc4bf89c0047a5e90f21e6f8eaffb4f6c148998f1740f30baf666bdae0a44",
                "policySnapshotDigest", modelProfiles.policySnapshotDigest(),
                "cases", List.of(Map.of(
                    "caseId", daCase.caseId(),
                    "datasetRowRef", daCase.datasetRowRef(),
                    "inputSchemaVersion", daCase.inputSchemaVersion(),
                    "expectedInputDigest", daCase.expectedInputDigest()
                )),
                "modelBindings", profileBindings
            )
        );
        var daProvenance = runDefinition(
            DA_PROVENANCE_RUN_ID,
            "1.0.0",
            "financial_synthetic",
            "financial_synthetic_processed_v1",
            "sha256:9afdc4bf89c0047a5e90f21e6f8eaffb4f6c148998f1740f30baf666bdae0a44",
            modelProfiles.policySnapshotDigest(),
            daContractDigest,
            daCase,
            modelProfiles.profiles().stream().map(profile -> profile.profileId()).collect(java.util.stream.Collectors.toSet())
        );
        this.runs = Map.of(
            baseline.evaluationRunId(), baseline,
            daProvenance.evaluationRunId(), daProvenance
        );
    }

    public Optional<AiEvaluationRunDefinition> find(String evaluationRunId) {
        return evaluationRunId == null ? Optional.empty() : Optional.ofNullable(runs.get(evaluationRunId));
    }

    public AiEvaluationReference resolve(AiEvaluationReference reference, Map<String, Object> input) {
        if (reference == null) {
            return null;
        }
        AiEvaluationRunDefinition run = find(reference.evaluationRunId())
            .orElseThrow(() -> new AiEvaluationRunMismatchException("AI_EVALUATION_RUN_NOT_FOUND"));
        AiEvaluationCaseDefinition evaluationCase = run.cases().get(reference.evalCaseId());
        if (evaluationCase == null) {
            throw new AiEvaluationRunMismatchException("AI_EVALUATION_CASE_NOT_FOUND");
        }
        String actualInputDigest = inputHasher.hash(input);
        if (!evaluationCase.expectedInputDigest().equals(actualInputDigest)) {
            throw new AiEvaluationRunMismatchException("AI_EVALUATION_CASE_INPUT_MISMATCH");
        }
        return new AiEvaluationReference(
            reference.evaluationRunId(), reference.evalCaseId(), null, run.contractDigest(),
            evaluationCase.expectedInputDigest(), actualInputDigest
        );
    }

    private String digest(ObjectMapper objectMapper, CanonicalValueHasher hasher, Object value) {
        try {
            return "sha256:" + hasher.hash(objectMapper.writeValueAsString(value));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Evaluation run contract could not be canonicalized", exception);
        }
    }

    private AiEvaluationRunDefinition runDefinition(String id, String version, String datasetId, String datasetVersion,
        String datasetDigest, String policySnapshotDigest, String contractDigest, AiEvaluationCaseDefinition evaluationCase,
        Set<String> modelProfileIds) {
        return new AiEvaluationRunDefinition(id, version, datasetId, datasetVersion, datasetDigest,
            policySnapshotDigest, contractDigest, Map.of(evaluationCase.caseId(), evaluationCase), modelProfileIds);
    }
}
