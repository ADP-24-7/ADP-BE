package com.adp.gateway.ai.application;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import com.adp.gateway.ai.domain.AiEvaluationBundle;
import com.adp.gateway.ai.domain.AiEvaluationBundleSource;
import com.adp.gateway.ai.domain.AiEvaluationRunDefinition;
import com.adp.gateway.ai.domain.AiModelProfile;
import com.adp.gateway.auth.domain.AuthPrincipal;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.adp.gateway.observability.GatewayObservability;
import com.adp.gateway.observability.GatewayObservability.AiEvaluationBundleExportOutcome;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
public class AiEvaluationBundleService {
    public static final String SCHEMA_VERSION = "adp-ai-evaluation-bundle/v1";
    static final int MAX_EXECUTION_COUNT = 10_000;

    private final AiEvaluationBundlePort bundlePort;
    private final AiEvaluationRunCatalog runCatalog;
    private final AiModelProfileCatalog modelProfileCatalog;
    private final AiEvaluationBundleCanonicalizer canonicalizer;
    private final GatewayObservability observability;

    public AiEvaluationBundleService(
        AiEvaluationBundlePort bundlePort,
        AiEvaluationRunCatalog runCatalog,
        AiModelProfileCatalog modelProfileCatalog,
        AiEvaluationBundleCanonicalizer canonicalizer,
        GatewayObservability observability
    ) {
        this.bundlePort = bundlePort;
        this.runCatalog = runCatalog;
        this.modelProfileCatalog = modelProfileCatalog;
        this.canonicalizer = canonicalizer;
        this.observability = observability;
    }

    public AiEvaluationBundle export(AuthPrincipal principal, String evaluationRunId) {
        requireInstitution(principal);
        AiEvaluationRunDefinition run = runCatalog.find(evaluationRunId).orElse(null);
        if (run == null) {
            observability.aiEvaluationBundleExport(AiEvaluationBundleExportOutcome.NOT_FOUND);
            throw new AiEvaluationBundleNotFoundException(evaluationRunId);
        }
        validateExpectedSize(run);
        List<AiEvaluationBundleSource> rows = bundlePort.load(
            evaluationRunId, principal.institutionId(), principal.workloadIds(), MAX_EXECUTION_COUNT + 1
        );
        if (rows.isEmpty()) {
            observability.aiEvaluationBundleExport(AiEvaluationBundleExportOutcome.NOT_FOUND);
            throw new AiEvaluationBundleNotFoundException(evaluationRunId);
        }
        validateCompleteness(run, rows);
        validateRunIdentity(run, rows);
        validateModelIdentity(run, rows);

        AiEvaluationBundleSource first = rows.getFirst();
        List<AiEvaluationBundle.ModelConfig> models = modelConfigs(rows);
        List<AiEvaluationBundle.CaseResult> caseResults = rows.stream()
            .map(this::caseResult)
            .toList();
        List<AiEvaluationBundle.RuntimeMetric> runtimeMetrics = rows.stream()
            .map(this::runtimeMetric)
            .toList();
        AiEvaluationBundle.FailureSummary failureSummary = failureSummary(rows);
        List<AiEvaluationBundle.TraceEntry> traceIndex = rows.stream()
            .map(this::traceEntry)
            .toList();
        var executionConfig = new AiEvaluationBundle.ExecutionConfig(
            first.evaluationRunId(), first.evaluationRunVersion(), first.evaluationContractDigest(),
            first.datasetId(), first.datasetVersion(), first.datasetDigest(), first.policySnapshotDigest(), models
        );
        String contentDigest = canonicalizer.digest(new BundleContent(
            SCHEMA_VERSION, executionConfig, caseResults, runtimeMetrics, failureSummary, traceIndex
        ));
        OffsetDateTime evidenceFrom = rows.stream().map(AiEvaluationBundleSource::createdAt)
            .min(Comparator.naturalOrder()).orElseThrow();
        OffsetDateTime evidenceTo = rows.stream().map(AiEvaluationBundleSource::updatedAt)
            .max(Comparator.naturalOrder()).orElseThrow();
        int caseCount = (int) rows.stream().map(AiEvaluationBundleSource::evalCaseId).distinct().count();
        String bundleId = "AI-EVAL-BUNDLE:" + first.evaluationRunId() + ":" + first.evaluationRunVersion();

        AiEvaluationBundle bundle = new AiEvaluationBundle(
            new AiEvaluationBundle.Manifest(
                SCHEMA_VERSION, bundleId, "1.0.0", contentDigest,
                first.evaluationRunId(), first.evaluationRunVersion(),
                rows.size(), caseCount, models.size(), evidenceTo, evidenceFrom, evidenceTo
            ),
            executionConfig,
            caseResults,
            runtimeMetrics,
            failureSummary,
            traceIndex
        );
        observability.aiEvaluationBundleExport(AiEvaluationBundleExportOutcome.SUCCESS);
        return bundle;
    }

    private List<AiEvaluationBundle.ModelConfig> modelConfigs(List<AiEvaluationBundleSource> rows) {
        var models = new LinkedHashMap<String, AiEvaluationBundle.ModelConfig>();
        for (AiEvaluationBundleSource row : rows) {
            var model = new AiEvaluationBundle.ModelConfig(
                row.profileId(), row.profileVersion(), row.profileDigest(), row.providerModelId(),
                row.providerModelVersion(), row.connectionProfileId(), row.maxTokens(), row.temperature(),
                row.samplingProfileVersion(), row.destinationProfileDigest()
            );
            AiEvaluationBundle.ModelConfig existing = models.putIfAbsent(row.profileId(), model);
            if (existing != null && !existing.equals(model)) {
                throw new IllegalStateException("Evaluation model configuration is inconsistent");
            }
        }
        return models.values().stream().sorted(Comparator.comparing(AiEvaluationBundle.ModelConfig::profileId)).toList();
    }

    private void validateCompleteness(AiEvaluationRunDefinition run, List<AiEvaluationBundleSource> rows) {
        if (rows.size() > MAX_EXECUTION_COUNT) {
            fail("AI_EVALUATION_BUNDLE_SIZE_LIMIT_EXCEEDED", AiEvaluationBundleExportOutcome.SIZE_LIMIT_EXCEEDED);
        }
        Set<CaseModelPair> expected = run.cases().keySet().stream()
            .flatMap(caseId -> run.modelProfileIds().stream().map(profileId -> new CaseModelPair(caseId, profileId)))
            .collect(Collectors.toUnmodifiableSet());
        Set<CaseModelPair> observed = rows.stream()
            .map(row -> new CaseModelPair(row.evalCaseId(), row.profileId()))
            .collect(Collectors.toUnmodifiableSet());
        boolean completeEvidence = rows.stream().allMatch(row -> "COMPLETE".equals(row.evidenceStatus()));
        if (!expected.equals(observed) || observed.size() != rows.size() || !completeEvidence) {
            fail("AI_EVALUATION_BUNDLE_INCOMPLETE", AiEvaluationBundleExportOutcome.INCOMPLETE);
        }
    }

    private void validateExpectedSize(AiEvaluationRunDefinition run) {
        long expectedCount = (long) run.cases().size() * run.modelProfileIds().size();
        if (expectedCount > MAX_EXECUTION_COUNT) {
            fail("AI_EVALUATION_BUNDLE_SIZE_LIMIT_EXCEEDED", AiEvaluationBundleExportOutcome.SIZE_LIMIT_EXCEEDED);
        }
    }

    private void validateRunIdentity(AiEvaluationRunDefinition run, List<AiEvaluationBundleSource> rows) {
        for (AiEvaluationBundleSource row : rows) {
            var expectedCase = run.cases().get(row.evalCaseId());
            if (!same(run.evaluationRunId(), row.evaluationRunId())
                || !same(run.runVersion(), row.evaluationRunVersion())
                || !same(run.contractDigest(), row.evaluationContractDigest())
                || !same(run.datasetId(), row.datasetId())
                || !same(run.datasetVersion(), row.datasetVersion())
                || !same(run.datasetDigest(), row.datasetDigest())
                || !same(run.policySnapshotDigest(), row.policySnapshotDigest())
                || expectedCase == null
                || !same(expectedCase.expectedInputDigest(), row.expectedInputDigest())
                || !same(expectedCase.expectedInputDigest(), row.actualInputDigest())) {
                fail("AI_EVALUATION_BUNDLE_PROVENANCE_MISMATCH",
                    AiEvaluationBundleExportOutcome.PROVENANCE_MISMATCH);
            }
        }
    }

    private void validateModelIdentity(AiEvaluationRunDefinition run, List<AiEvaluationBundleSource> rows) {
        for (AiEvaluationBundleSource row : rows) {
            AiModelProfile profile = modelProfileCatalog.findByProfileId(row.profileId()).orElse(null);
            if (profile == null || !run.modelProfileIds().contains(row.profileId())
                || !same(profile.profileVersion(), row.profileVersion())
                || !same(profile.modelProfileDigest(), row.profileDigest())
                || !same(profile.modelId(), row.providerModelId())
                || !same(profile.modelVersion(), row.providerModelVersion())
                || !same(profile.providerConnectionProfileId(), row.connectionProfileId())
                || !same(profile.maxTokens(), row.maxTokens())
                || row.temperature() == null
                || Double.compare(profile.temperature(), row.temperature()) != 0
                || !same(profile.profileVersion(), row.samplingProfileVersion())
                || !same(profile.destinationProfileDigest(), row.destinationProfileDigest())) {
                fail("AI_EVALUATION_BUNDLE_MODEL_MISMATCH", AiEvaluationBundleExportOutcome.MODEL_MISMATCH);
            }
        }
    }

    private boolean same(Object left, Object right) {
        return java.util.Objects.equals(left, right);
    }

    private AiEvaluationBundle.CaseResult caseResult(AiEvaluationBundleSource row) {
        return new AiEvaluationBundle.CaseResult(
            row.executionId(), row.evalCaseId(), row.profileId(), row.runtimeStatus(), row.finalAction(),
            row.responseGuardStatus(), row.controlledDeliveryStatus(), row.providerStatus(), row.errorCategory(),
            row.evidenceStatus(), row.expectedInputDigest(), row.actualInputDigest()
        );
    }

    private AiEvaluationBundle.RuntimeMetric runtimeMetric(AiEvaluationBundleSource row) {
        return new AiEvaluationBundle.RuntimeMetric(
            row.executionId(), row.evalCaseId(), row.profileId(), row.measurementType(),
            row.fullResponseLatencyMillis(), row.attemptElapsedMillis(), row.initialRuntimeLatencyMillis(),
            row.inputTokens(), row.outputTokens(), row.totalTokens(), row.tokenUsageStatus(),
            row.providerHttpStatus(), row.providerStatus(), row.errorCategory()
        );
    }

    private AiEvaluationBundle.TraceEntry traceEntry(AiEvaluationBundleSource row) {
        return new AiEvaluationBundle.TraceEntry(
            row.executionId(), row.decisionId(), row.connectorExecutionId(), row.providerRequestDigest(),
            row.providerResponseDigest(), row.createdAt(), row.updatedAt()
        );
    }

    private AiEvaluationBundle.FailureSummary failureSummary(List<AiEvaluationBundleSource> rows) {
        Map<String, Integer> byErrorCategory = new TreeMap<>();
        for (AiEvaluationBundleSource row : rows) {
            if (row.errorCategory() != null && !"NONE".equals(row.errorCategory())) {
                byErrorCategory.merge(row.errorCategory(), 1, Integer::sum);
            }
        }
        return new AiEvaluationBundle.FailureSummary(
            rows.size(),
            count(rows, row -> "FAILED".equals(row.providerStatus())),
            count(rows, row -> "SENT_UNKNOWN".equals(row.providerStatus())),
            count(rows, row -> "NOT_ATTEMPTED".equals(row.measurementType())),
            count(rows, row -> "PARTIAL".equals(row.evidenceStatus())),
            byErrorCategory
        );
    }

    private int count(
        List<AiEvaluationBundleSource> rows,
        java.util.function.Predicate<AiEvaluationBundleSource> predicate
    ) {
        return (int) rows.stream().filter(predicate).count();
    }

    private void fail(String reasonCode, AiEvaluationBundleExportOutcome outcome) {
        observability.aiEvaluationBundleExport(outcome);
        throw new AiEvaluationBundleIntegrityException(reasonCode);
    }

    private void requireInstitution(AuthPrincipal principal) {
        if (principal.institutionId() == null || principal.institutionId().isBlank()) {
            throw new AccessDeniedException("AI evaluation bundle institution scope is required");
        }
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    private record BundleContent(
        String schemaVersion,
        AiEvaluationBundle.ExecutionConfig executionConfig,
        List<AiEvaluationBundle.CaseResult> caseResults,
        List<AiEvaluationBundle.RuntimeMetric> runtimeMetrics,
        AiEvaluationBundle.FailureSummary failureSummary,
        List<AiEvaluationBundle.TraceEntry> traceIndex
    ) {
    }

    private record CaseModelPair(String caseId, String profileId) {
    }
}
