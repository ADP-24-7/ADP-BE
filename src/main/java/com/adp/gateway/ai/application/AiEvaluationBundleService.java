package com.adp.gateway.ai.application;

import java.time.Clock;
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
import com.adp.gateway.ai.domain.AiEvaluationRunReadiness;
import com.adp.gateway.ai.domain.AiModelProfile;
import com.adp.gateway.auth.domain.AuthPrincipal;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.adp.gateway.observability.GatewayObservability;
import com.adp.gateway.observability.GatewayObservability.AiEvaluationBundleExportOutcome;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiEvaluationBundleService {
    @org.springframework.beans.factory.annotation.Autowired
    private AiEvaluationContractPort contractPort;
    public static final String SCHEMA_VERSION = "adp-ai-evaluation-bundle/v1";
    static final int MAX_EXECUTION_COUNT = 10_000;

    private final AiEvaluationBundlePort bundlePort;
    private final AiEvaluationRunCatalog runCatalog;
    private final AiModelProfileCatalog modelProfileCatalog;
    private final AiEvaluationBundleCanonicalizer canonicalizer;
    private final GatewayObservability observability;
    private final Clock clock;

    public AiEvaluationBundleService(
        AiEvaluationBundlePort bundlePort,
        AiEvaluationRunCatalog runCatalog,
        AiModelProfileCatalog modelProfileCatalog,
        AiEvaluationBundleCanonicalizer canonicalizer,
        GatewayObservability observability,
        Clock clock
    ) {
        this.bundlePort = bundlePort;
        this.runCatalog = runCatalog;
        this.modelProfileCatalog = modelProfileCatalog;
        this.canonicalizer = canonicalizer;
        this.observability = observability;
        this.clock = clock;
    }

    public AiEvaluationBundle export(AuthPrincipal principal, String evaluationRunId) {
        requireInstitution(principal);
        AiEvaluationRunDefinition run = runCatalog.find(evaluationRunId).orElse(null);
        if (run == null) {
            observability.aiEvaluationBundleExport(AiEvaluationBundleExportOutcome.NOT_FOUND);
            throw new AiEvaluationBundleNotFoundException(evaluationRunId);
        }
        List<AiEvaluationBundleSource> rows = bundlePort.load(
            evaluationRunId, principal.institutionId(), principal.workloadIds(), MAX_EXECUTION_COUNT + 1
        );
        if (rows.isEmpty()) {
            observability.aiEvaluationBundleExport(AiEvaluationBundleExportOutcome.NOT_FOUND);
            throw new AiEvaluationBundleNotFoundException(evaluationRunId);
        }
        validateExpectedSize(run);
        validateCompleteness(run, rows);
        validateRunIdentity(run, rows);
        validateModelIdentity(run, rows);
        rows = rows.stream()
            .sorted(Comparator.comparing(AiEvaluationBundleSource::evalCaseId)
                .thenComparing(AiEvaluationBundleSource::profileId)
                .thenComparing(AiEvaluationBundleSource::executionId))
            .toList();

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
        Map<String, Object> contractEvidence = contractPort == null ? null : contractPort.evidence(
            evaluationRunId, rows.stream().map(AiEvaluationBundleSource::executionId).toList());
        String schemaVersion = contractEvidence == null ? SCHEMA_VERSION : "adp-ai-evaluation-bundle/v2";
        Map<String, Object> content = new TreeMap<>();
        content.put("schema_version", schemaVersion);
        content.put("execution_config", executionConfig);
        content.put("case_results", caseResults);
        content.put("runtime_metrics", runtimeMetrics);
        content.put("failure_summary", failureSummary);
        content.put("trace_index", traceIndex);
        if (contractEvidence != null) content.put("contract_evidence", contractEvidence);
        String contentDigest = canonicalizer.digest(content);
        OffsetDateTime executionFrom = rows.stream().map(AiEvaluationBundleSource::createdAt)
            .min(Comparator.naturalOrder()).orElseThrow();
        OffsetDateTime executionCutoffAt = rows.stream().map(AiEvaluationBundleSource::updatedAt)
            .max(Comparator.naturalOrder()).orElseThrow();
        int caseCount = (int) rows.stream().map(AiEvaluationBundleSource::evalCaseId).distinct().count();
        String bundleId = "AI-EVAL-BUNDLE:" + first.evaluationRunId() + ":" + first.evaluationRunVersion();

        AiEvaluationBundle bundle = new AiEvaluationBundle(
            new AiEvaluationBundle.Manifest(
                schemaVersion, bundleId, contractEvidence == null ? "1.0.0" : "2.0.0", contentDigest,
                first.evaluationRunId(), first.evaluationRunVersion(),
                rows.size(), caseCount, models.size(), OffsetDateTime.now(clock), executionFrom, executionCutoffAt
            ),
            executionConfig,
            caseResults,
            runtimeMetrics,
            failureSummary,
            traceIndex,
            contractEvidence
        );
        observability.aiEvaluationBundleExport(AiEvaluationBundleExportOutcome.SUCCESS);
        return bundle;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public AiEvaluationRunReadiness readiness(AuthPrincipal principal, String evaluationRunId) {
        requireInstitution(principal);
        AiEvaluationRunDefinition run = runCatalog.find(evaluationRunId)
            .orElseThrow(() -> new AiEvaluationBundleNotFoundException(evaluationRunId));
        validateExpectedSize(run);
        List<AiEvaluationBundleSource> rows = bundlePort.load(
            evaluationRunId, principal.institutionId(), principal.workloadIds(), MAX_EXECUTION_COUNT + 1
        );
        long storedExecutionCount = bundlePort.countStored(
            evaluationRunId, principal.institutionId(), principal.workloadIds()
        );
        Set<CaseModelPair> expected = expectedPairs(run);
        Map<CaseModelPair, AiEvaluationBundleSource> observed = rows.stream()
            .filter(row -> expected.contains(new CaseModelPair(row.evalCaseId(), row.profileId())))
            .collect(Collectors.toMap(
                row -> new CaseModelPair(row.evalCaseId(), row.profileId()),
                row -> row,
                (first, ignored) -> first
            ));
        List<AiEvaluationRunReadiness.CaseModelEvidence> matrix = expected.stream()
            .sorted(Comparator.comparing(CaseModelPair::caseId).thenComparing(CaseModelPair::profileId))
            .map(pair -> readinessEntry(pair, observed.get(pair)))
            .toList();
        int completeEvidenceCount = (int) rows.stream()
            .filter(row -> "COMPLETE".equals(row.evidenceStatus()))
            .count();
        int unexpectedExecutionCount = (int) rows.stream()
            .filter(row -> !expected.contains(new CaseModelPair(row.evalCaseId(), row.profileId())))
            .count();
        AiEvaluationRunReadiness.Status status = readinessStatus(run, rows);
        return new AiEvaluationRunReadiness(
            run.evaluationRunId(), run.runVersion(), status,
            status == AiEvaluationRunReadiness.Status.READY,
            expected.size(), storedExecutionCount, rows.size(), completeEvidenceCount,
            expected.size() - observed.size(), unexpectedExecutionCount, matrix
        );
    }

    private AiEvaluationRunReadiness.CaseModelEvidence readinessEntry(
        CaseModelPair pair,
        AiEvaluationBundleSource row
    ) {
        return row == null
            ? new AiEvaluationRunReadiness.CaseModelEvidence(
                pair.caseId(), pair.profileId(), null, null, null, null
            )
            : new AiEvaluationRunReadiness.CaseModelEvidence(
                pair.caseId(), pair.profileId(), row.executionId(), row.runtimeStatus(),
                row.providerStatus(), row.evidenceStatus()
            );
    }

    private AiEvaluationRunReadiness.Status readinessStatus(
        AiEvaluationRunDefinition run,
        List<AiEvaluationBundleSource> rows
    ) {
        if (rows.isEmpty()) {
            return AiEvaluationRunReadiness.Status.NOT_STARTED;
        }
        if (!hasCompleteMatrix(run, rows)) {
            return AiEvaluationRunReadiness.Status.INCOMPLETE;
        }
        if (!hasValidRunIdentity(run, rows)) {
            return AiEvaluationRunReadiness.Status.PROVENANCE_MISMATCH;
        }
        if (!hasValidModelIdentity(run, rows)) {
            return AiEvaluationRunReadiness.Status.MODEL_MISMATCH;
        }
        return AiEvaluationRunReadiness.Status.READY;
    }

    private List<AiEvaluationBundle.ModelConfig> modelConfigs(List<AiEvaluationBundleSource> rows) {
        var models = new LinkedHashMap<String, AiEvaluationBundle.ModelConfig>();
        for (AiEvaluationBundleSource row : rows) {
            var model = new AiEvaluationBundle.ModelConfig(
                row.profileId(), row.profileVersion(), row.profileDigest(), row.providerModelId(),
                row.providerModelVersion(), row.connectionProfileId(), row.maxTokens(), row.temperature(),
                row.samplingProfileVersion(), row.destinationProfileDigest(),
                modelProfileCatalog.findByProfileId(row.profileId()).orElseThrow().destinationProfileId(), "NVIDIA"
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
        if (!hasCompleteMatrix(run, rows)) {
            fail("AI_EVALUATION_BUNDLE_INCOMPLETE", AiEvaluationBundleExportOutcome.INCOMPLETE);
        }
    }

    private boolean hasCompleteMatrix(AiEvaluationRunDefinition run, List<AiEvaluationBundleSource> rows) {
        Set<CaseModelPair> expected = expectedPairs(run);
        Set<CaseModelPair> observed = rows.stream()
            .map(row -> new CaseModelPair(row.evalCaseId(), row.profileId()))
            .collect(Collectors.toUnmodifiableSet());
        boolean completeEvidence = rows.stream().allMatch(row -> "COMPLETE".equals(row.evidenceStatus()));
        return expected.equals(observed) && observed.size() == rows.size() && completeEvidence;
    }

    private Set<CaseModelPair> expectedPairs(AiEvaluationRunDefinition run) {
        return run.cases().keySet().stream()
            .flatMap(caseId -> run.modelProfileIds().stream().map(profileId -> new CaseModelPair(caseId, profileId)))
            .collect(Collectors.toUnmodifiableSet());
    }

    private void validateExpectedSize(AiEvaluationRunDefinition run) {
        long expectedCount = (long) run.cases().size() * run.modelProfileIds().size();
        if (expectedCount > MAX_EXECUTION_COUNT) {
            fail("AI_EVALUATION_BUNDLE_SIZE_LIMIT_EXCEEDED", AiEvaluationBundleExportOutcome.SIZE_LIMIT_EXCEEDED);
        }
    }

    private void validateRunIdentity(AiEvaluationRunDefinition run, List<AiEvaluationBundleSource> rows) {
        if (!hasValidRunIdentity(run, rows)) {
            fail("AI_EVALUATION_BUNDLE_PROVENANCE_MISMATCH",
                AiEvaluationBundleExportOutcome.PROVENANCE_MISMATCH);
        }
    }

    private boolean hasValidRunIdentity(AiEvaluationRunDefinition run, List<AiEvaluationBundleSource> rows) {
        return rows.stream().allMatch(row -> {
            var expectedCase = run.cases().get(row.evalCaseId());
            return same(run.evaluationRunId(), row.evaluationRunId())
                && same(run.runVersion(), row.evaluationRunVersion())
                && same(run.contractDigest(), row.evaluationContractDigest())
                && same(run.datasetId(), row.datasetId())
                && same(run.datasetVersion(), row.datasetVersion())
                && same(run.datasetDigest(), row.datasetDigest())
                && same(run.policySnapshotDigest(), row.policySnapshotDigest())
                && expectedCase != null
                && same(expectedCase.expectedInputDigest(), row.expectedInputDigest())
                && same(expectedCase.expectedInputDigest(), row.actualInputDigest());
        });
    }

    private void validateModelIdentity(AiEvaluationRunDefinition run, List<AiEvaluationBundleSource> rows) {
        if (!hasValidModelIdentity(run, rows)) {
            fail("AI_EVALUATION_BUNDLE_MODEL_MISMATCH", AiEvaluationBundleExportOutcome.MODEL_MISMATCH);
        }
    }

    private boolean hasValidModelIdentity(AiEvaluationRunDefinition run, List<AiEvaluationBundleSource> rows) {
        return rows.stream().allMatch(row -> {
            AiModelProfile profile = modelProfileCatalog.findByProfileId(row.profileId()).orElse(null);
            return profile != null
                && run.modelProfileIds().contains(row.profileId())
                && same(profile.profileVersion(), row.profileVersion())
                && same(profile.modelProfileDigest(), row.profileDigest())
                && same(profile.modelId(), row.providerModelId())
                && same(profile.modelVersion(), row.providerModelVersion())
                && same(profile.providerConnectionProfileId(), row.connectionProfileId())
                && same(profile.maxTokens(), row.maxTokens())
                && row.temperature() != null
                && Double.compare(profile.temperature(), row.temperature()) == 0
                && same(profile.profileVersion(), row.samplingProfileVersion())
                && same(profile.destinationProfileDigest(), row.destinationProfileDigest());
        });
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
            row.providerResponseDigest(), row.createdAt(), row.updatedAt(),
            row.transformExecutionId(), row.outboundPayloadId(), row.outboundGuardStatus()
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
