package com.adp.gateway.ai.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import com.adp.gateway.ai.domain.AiEvaluationReference;
import com.adp.gateway.context.application.CanonicalValueHasher;
import com.adp.gateway.runtime.application.RuntimeInputHasher;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class AiEvaluationRunCatalogTests {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CanonicalValueHasher hasher = new CanonicalValueHasher();
    private final AiModelProfileCatalog models = new AiModelProfileCatalog(objectMapper, hasher);
    private final AiEvaluationRunCatalog runs = new AiEvaluationRunCatalog(
        models, new RuntimeInputHasher(objectMapper), objectMapper, hasher
    );

    @Test
    void resolvesServerOwnedCaseInputAndContractDigest() {
        var resolved = runs.resolve(reference(), Map.of(
            "prompt", "승인된 고객 정보를 간단히 요약하세요"
        ));

        assertThat(resolved.evaluationContractDigest()).matches("sha256:[0-9a-f]{64}");
        assertThat(resolved.actualInputDigest()).isEqualTo(resolved.expectedInputDigest());
    }

    @Test
    void rejectsDifferentInputForTheSameCaseId() {
        assertThatThrownBy(() -> runs.resolve(reference(), Map.of("prompt", "전혀 다른 질문")))
            .isInstanceOf(AiEvaluationRunMismatchException.class)
            .satisfies(exception -> assertThat(
                ((AiEvaluationRunMismatchException) exception).reasonCode()
            ).isEqualTo("AI_EVALUATION_CASE_INPUT_MISMATCH"));
    }

    @Test
    void resolvesDaProvenanceCaseWithPinnedSourceDigest() {
        var run = runs.find(AiEvaluationRunCatalog.DA_PROVENANCE_RUN_ID).orElseThrow();
        var evaluationCase = run.cases().get(AiEvaluationRunCatalog.DA_PROVENANCE_CASE_ID);
        var resolved = runs.resolve(new AiEvaluationReference(
            run.evaluationRunId(), evaluationCase.caseId(), null, null, null, null
        ), Map.of("prompt", AiEvaluationPrompt.TEXT));

        assertThat(evaluationCase.datasetRowRef()).isEqualTo(AiEvaluationRunCatalog.DA_PROVENANCE_DATASET_ROW_REF);
        assertThat(evaluationCase.datasetRowRef()).endsWith(
            "sha256:18f0831cf7e970ad9d8c376d3a3877c86612270dd259745b8a14225761de5dfe"
        );
        assertThat(resolved.actualInputDigest()).isEqualTo(resolved.expectedInputDigest());
        assertThat(run.modelProfileIds()).hasSize(3);
    }

    private AiEvaluationReference reference() {
        return new AiEvaluationReference(
            AiEvaluationRunCatalog.BASELINE_RUN_ID,
            AiEvaluationRunCatalog.BASELINE_CASE_ID,
            null,
            null,
            null,
            null
        );
    }
}
