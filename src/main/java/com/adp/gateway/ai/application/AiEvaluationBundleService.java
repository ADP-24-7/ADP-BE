package com.adp.gateway.ai.application;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;

import com.adp.gateway.ai.domain.AiEvaluationBundle;
import com.adp.gateway.ai.domain.AiEvaluationBundleSource;
import com.adp.gateway.auth.domain.AuthPrincipal;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
public class AiEvaluationBundleService {
    public static final String SCHEMA_VERSION = "adp-ai-evaluation-bundle/v1";

    private final AiEvaluationBundlePort bundlePort;
    private final ObjectMapper objectMapper;

    public AiEvaluationBundleService(AiEvaluationBundlePort bundlePort, ObjectMapper objectMapper) {
        this.bundlePort = bundlePort;
        this.objectMapper = objectMapper;
    }

    public AiEvaluationBundle export(AuthPrincipal principal, String evaluationRunId) {
        requireInstitution(principal);
        List<AiEvaluationBundleSource> rows = bundlePort.load(
            evaluationRunId, principal.institutionId(), principal.workloadIds()
        );
        if (rows.isEmpty()) {
            throw new AiEvaluationBundleNotFoundException(evaluationRunId);
        }
        validateRunIdentity(rows);

        AiEvaluationBundleSource first = rows.getFirst();
        List<AiEvaluationBundle.ModelConfig> models = modelConfigs(rows);
        List<AiEvaluationBundle.CaseResult> caseResults = rows.stream()
            .map(this::caseResult)
            .toList();
        List<AiEvaluationBundle.RuntimeMetric> runtimeMetrics = rows.stream()
            .map(this::runtimeMetric)
            .toList();
        List<AiEvaluationBundle.TraceEntry> traceIndex = rows.stream()
            .map(this::traceEntry)
            .toList();
        var executionConfig = new AiEvaluationBundle.ExecutionConfig(
            first.evaluationRunId(), first.evaluationRunVersion(), first.evaluationContractDigest(),
            first.datasetId(), first.datasetVersion(), first.datasetDigest(), first.policySnapshotDigest(), models
        );
        String contentDigest = digest(new BundleContent(
            SCHEMA_VERSION, executionConfig, caseResults, runtimeMetrics, traceIndex
        ));
        OffsetDateTime evidenceFrom = rows.stream().map(AiEvaluationBundleSource::createdAt)
            .min(Comparator.naturalOrder()).orElseThrow();
        OffsetDateTime evidenceTo = rows.stream().map(AiEvaluationBundleSource::updatedAt)
            .max(Comparator.naturalOrder()).orElseThrow();
        int caseCount = (int) rows.stream().map(AiEvaluationBundleSource::evalCaseId).distinct().count();

        return new AiEvaluationBundle(
            new AiEvaluationBundle.Manifest(
                SCHEMA_VERSION, contentDigest, first.evaluationRunId(), first.evaluationRunVersion(),
                rows.size(), caseCount, models.size(), evidenceFrom, evidenceTo
            ),
            executionConfig,
            caseResults,
            runtimeMetrics,
            traceIndex
        );
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

    private void validateRunIdentity(List<AiEvaluationBundleSource> rows) {
        AiEvaluationBundleSource first = rows.getFirst();
        for (AiEvaluationBundleSource row : rows) {
            if (!same(first.evaluationRunId(), row.evaluationRunId())
                || !same(first.evaluationRunVersion(), row.evaluationRunVersion())
                || !same(first.evaluationContractDigest(), row.evaluationContractDigest())
                || !same(first.datasetId(), row.datasetId())
                || !same(first.datasetVersion(), row.datasetVersion())
                || !same(first.datasetDigest(), row.datasetDigest())
                || !same(first.policySnapshotDigest(), row.policySnapshotDigest())) {
                throw new IllegalStateException("Evaluation run provenance is inconsistent");
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

    private String digest(BundleContent content) {
        try {
            byte[] serialized = objectMapper.writeValueAsBytes(content);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(serialized);
            return "sha256:" + HexFormat.of().formatHex(digest);
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Unable to digest AI evaluation bundle", exception);
        }
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
        List<AiEvaluationBundle.TraceEntry> traceIndex
    ) {
    }
}
