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
    public static final String EXPERIMENT_02_V2_RUN_ID = "ai-experiment-02-financial-regulatory-v2";
    public static final String EXPERIMENT_02_V3_RUN_ID = "ai-experiment-02-financial-regulatory-v3";
    public static final String EXPERIMENT_02_V4_RUN_ID = "ai-experiment-02-financial-regulatory-v4";
    public static final String EXPERIMENT_02_RUN_ID = "ai-experiment-02-financial-regulatory-v5";
    public static final String EXPERIMENT_02_P1 = "financial-regulatory-p1-customer-10861";
    public static final String EXPERIMENT_02_P2 = "financial-regulatory-p2-customer-10832";
    public static final String EXPERIMENT_02_P3 = "financial-regulatory-p3-customer-10202";
    public static final String EXPERIMENT_02_P1_ROW_REF =
        "financial_synthetic_processed_v1:customers.csv#CustomerID=10861:sha256:db19a727415717a1ca271cea5bdb39ccdc6af4686a033eeb631ae4c83447401b";
    public static final String EXPERIMENT_02_P3_ROW_REF =
        "financial_synthetic_processed_v1:customers.csv#CustomerID=10202:sha256:2e794d37166b1eae5e50e1703d62d442174b61464d74c96dd8e7778f15e1149f";

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
        var e2Cases = Map.of(
            EXPERIMENT_02_P1, new AiEvaluationCaseDefinition(EXPERIMENT_02_P1, EXPERIMENT_02_P1_ROW_REF,
                "ai-evaluation-input/v1", expectedInputDigest),
            EXPERIMENT_02_P2, new AiEvaluationCaseDefinition(EXPERIMENT_02_P2, DA_PROVENANCE_DATASET_ROW_REF,
                "ai-evaluation-input/v1", expectedInputDigest),
            EXPERIMENT_02_P3, new AiEvaluationCaseDefinition(EXPERIMENT_02_P3, EXPERIMENT_02_P3_ROW_REF,
                "ai-evaluation-input/v1", expectedInputDigest)
        );
        String e2V2ContractDigest = digest(
            objectMapper.copy().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true), hasher,
            Map.of("runId", EXPERIMENT_02_V2_RUN_ID, "runVersion", "2.0.0",
                "datasetId", "financial_synthetic", "datasetVersion", "financial_synthetic_processed_v1",
                "datasetDigest", "sha256:9afdc4bf89c0047a5e90f21e6f8eaffb4f6c148998f1740f30baf666bdae0a44",
                "policySnapshotDigest", modelProfiles.policySnapshotDigest(),
                "cases", e2Cases.values().stream().sorted(java.util.Comparator.comparing(AiEvaluationCaseDefinition::caseId)).toList(),
                "modelBindings", profileBindings)
        );
        var experiment02V2 = new AiEvaluationRunDefinition(EXPERIMENT_02_V2_RUN_ID, "2.0.0", "financial_synthetic",
            "financial_synthetic_processed_v1",
            "sha256:9afdc4bf89c0047a5e90f21e6f8eaffb4f6c148998f1740f30baf666bdae0a44",
            modelProfiles.policySnapshotDigest(), e2V2ContractDigest, e2Cases,
            modelProfiles.profiles().stream().map(profile -> profile.profileId()).collect(java.util.stream.Collectors.toSet()));
        String e2V3ContractDigest = digest(
            objectMapper.copy().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true), hasher,
            Map.of("runId", EXPERIMENT_02_V3_RUN_ID, "runVersion", "3.0.0",
                "datasetId", "financial_synthetic", "datasetVersion", "financial_synthetic_processed_v1",
                "datasetDigest", "sha256:9afdc4bf89c0047a5e90f21e6f8eaffb4f6c148998f1740f30baf666bdae0a44",
                "policySnapshotDigest", modelProfiles.policySnapshotDigest(),
                "cases", e2Cases.values().stream().sorted(java.util.Comparator.comparing(AiEvaluationCaseDefinition::caseId)).toList(),
                "modelBindings", profileBindings)
        );
        var experiment02V3 = new AiEvaluationRunDefinition(EXPERIMENT_02_V3_RUN_ID, "3.0.0", "financial_synthetic",
            "financial_synthetic_processed_v1",
            "sha256:9afdc4bf89c0047a5e90f21e6f8eaffb4f6c148998f1740f30baf666bdae0a44",
            modelProfiles.policySnapshotDigest(), e2V3ContractDigest, e2Cases,
            modelProfiles.profiles().stream().map(profile -> profile.profileId()).collect(java.util.stream.Collectors.toSet()));
        String e2V4ContractDigest = digest(
            objectMapper.copy().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true), hasher,
            Map.of("runId", EXPERIMENT_02_V4_RUN_ID, "runVersion", "4.0.0",
                "datasetId", "financial_synthetic", "datasetVersion", "financial_synthetic_processed_v1",
                "datasetDigest", "sha256:9afdc4bf89c0047a5e90f21e6f8eaffb4f6c148998f1740f30baf666bdae0a44",
                "policySnapshotDigest", modelProfiles.policySnapshotDigest(),
                "cases", e2Cases.values().stream().sorted(java.util.Comparator.comparing(AiEvaluationCaseDefinition::caseId)).toList(),
                "modelBindings", profileBindings,
                "egressEvidenceDigest", "sha256:ab100dde0147c22177b3e7842cf3dc69a8d52fe4ad435a75902ac291f59b2b2f")
        );
        var experiment02V4 = new AiEvaluationRunDefinition(EXPERIMENT_02_V4_RUN_ID, "4.0.0", "financial_synthetic",
            "financial_synthetic_processed_v1",
            "sha256:9afdc4bf89c0047a5e90f21e6f8eaffb4f6c148998f1740f30baf666bdae0a44",
            modelProfiles.policySnapshotDigest(), e2V4ContractDigest, e2Cases,
            modelProfiles.profiles().stream().map(profile -> profile.profileId()).collect(java.util.stream.Collectors.toSet()));
        String e2ContractDigest = digest(
            objectMapper.copy().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true), hasher,
            Map.ofEntries(
                Map.entry("runId", EXPERIMENT_02_RUN_ID), Map.entry("runVersion", "5.0.0"),
                Map.entry("datasetId", "financial_synthetic"),
                Map.entry("datasetVersion", "financial_synthetic_processed_v1"),
                Map.entry("datasetDigest", "sha256:9afdc4bf89c0047a5e90f21e6f8eaffb4f6c148998f1740f30baf666bdae0a44"),
                Map.entry("policySnapshotDigest", modelProfiles.policySnapshotDigest()),
                Map.entry("cases", e2Cases.values().stream().sorted(java.util.Comparator.comparing(AiEvaluationCaseDefinition::caseId)).toList()),
                Map.entry("modelBindings", profileBindings),
                Map.entry("egressEvidenceDigest", "sha256:ab100dde0147c22177b3e7842cf3dc69a8d52fe4ad435a75902ac291f59b2b2f"),
                Map.entry("temporalProvenanceDigest", "sha256:7deb467f14d4b054f7f6106d8c874185050173b41e8a1c0b3411de073f940e42")
            )
        );
        var experiment02 = new AiEvaluationRunDefinition(EXPERIMENT_02_RUN_ID, "5.0.0", "financial_synthetic",
            "financial_synthetic_processed_v1",
            "sha256:9afdc4bf89c0047a5e90f21e6f8eaffb4f6c148998f1740f30baf666bdae0a44",
            modelProfiles.policySnapshotDigest(), e2ContractDigest, e2Cases,
            modelProfiles.profiles().stream().map(profile -> profile.profileId()).collect(java.util.stream.Collectors.toSet()));
        this.runs = Map.of(
            baseline.evaluationRunId(), baseline,
            daProvenance.evaluationRunId(), daProvenance,
            experiment02V2.evaluationRunId(), experiment02V2,
            experiment02V3.evaluationRunId(), experiment02V3,
            experiment02V4.evaluationRunId(), experiment02V4,
            experiment02.evaluationRunId(), experiment02
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
