package com.adp.gateway.ai.application;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.adp.gateway.ai.domain.AiCalibrationEvidence;
import com.adp.gateway.ai.domain.AiCalibrationFindingSource;
import com.adp.gateway.ai.domain.AiCalibrationGuardSource;
import com.adp.gateway.ai.domain.AiEvaluationBundle;
import com.adp.gateway.auth.domain.AuthPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiCalibrationEvidenceService {
    public static final String SCHEMA_VERSION = "adp-ai-calibration-evidence/v1";

    private final AiEvaluationBundleService bundleService;
    private final AiCalibrationEvidencePort evidencePort;
    private final AiEvaluationBundleCanonicalizer canonicalizer;
    private final Clock clock;

    public AiCalibrationEvidenceService(
        AiEvaluationBundleService bundleService,
        AiCalibrationEvidencePort evidencePort,
        AiEvaluationBundleCanonicalizer canonicalizer,
        Clock clock
    ) {
        this.bundleService = bundleService;
        this.evidencePort = evidencePort;
        this.canonicalizer = canonicalizer;
        this.clock = clock;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public AiCalibrationEvidence export(AuthPrincipal principal, String evaluationRunId) {
        AiEvaluationBundle bundle = bundleService.export(principal, evaluationRunId);
        Set<String> executionIds = bundle.caseResults().stream()
            .map(AiEvaluationBundle.CaseResult::executionId)
            .collect(Collectors.toUnmodifiableSet());
        Map<String, AiCalibrationGuardSource> guards = evidencePort.loadGuards(
            executionIds, principal.institutionId(), principal.workloadIds()
        ).stream().collect(Collectors.toUnmodifiableMap(
            AiCalibrationGuardSource::executionId,
            Function.identity(),
            (left, right) -> {
                throw new IllegalStateException("Multiple response guard results exist for one evaluation execution");
            }
        ));
        Map<String, List<AiCalibrationFindingSource>> findings = evidencePort.loadFindings(
            executionIds, principal.institutionId(), principal.workloadIds()
        ).stream().collect(Collectors.groupingBy(AiCalibrationFindingSource::executionId));

        List<AiCalibrationEvidence.ExecutionEvidence> executions = bundle.caseResults().stream()
            .sorted(Comparator.comparing(AiEvaluationBundle.CaseResult::evalCaseId)
                .thenComparing(AiEvaluationBundle.CaseResult::modelProfileId)
                .thenComparing(AiEvaluationBundle.CaseResult::executionId))
            .map(result -> executionEvidence(result, guards.get(result.executionId()),
                findings.getOrDefault(result.executionId(), List.of())))
            .toList();
        List<String> readinessReasons = readinessReasons(executions, guards.size(), executionIds.size());
        boolean calibrationReady = readinessReasons.isEmpty();
        OffsetDateTime executionFrom = bundle.manifest().executionFrom();
        OffsetDateTime executionCutoffAt = bundle.manifest().executionCutoffAt();

        Map<String, Object> content = new TreeMap<>();
        content.put("schema_version", SCHEMA_VERSION);
        content.put("evaluation_run_id", bundle.manifest().evaluationRunId());
        content.put("evaluation_run_version", bundle.manifest().evaluationRunVersion());
        content.put("execution_count", executions.size());
        content.put("execution_from", executionFrom);
        content.put("execution_cutoff_at", executionCutoffAt);
        content.put("calibration_ready", calibrationReady);
        content.put("readiness_reason_codes", readinessReasons);
        content.put("executions", executions);
        String digest = canonicalizer.digest(content);
        OffsetDateTime generatedAt = OffsetDateTime.now(clock);

        return new AiCalibrationEvidence(
            new AiCalibrationEvidence.Manifest(
                SCHEMA_VERSION,
                digest,
                bundle.manifest().evaluationRunId(),
                bundle.manifest().evaluationRunVersion(),
                executions.size(),
                generatedAt,
                executionFrom,
                executionCutoffAt
            ),
            calibrationReady,
            readinessReasons,
            executions
        );
    }

    private AiCalibrationEvidence.ExecutionEvidence executionEvidence(
        AiEvaluationBundle.CaseResult result,
        AiCalibrationGuardSource guard,
        List<AiCalibrationFindingSource> findings
    ) {
        List<AiCalibrationEvidence.FindingGroup> groups = findings.stream()
            .collect(Collectors.groupingBy(FindingGroupKey::from))
            .entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(entry -> new AiCalibrationEvidence.FindingGroup(
                entry.getKey().findingType(),
                entry.getKey().sourceDataClass(),
                entry.getKey().transformStrategy(),
                entry.getKey().fieldTreatment(),
                entry.getValue().size()
            ))
            .toList();
        int missingMetadataCount = (int) findings.stream()
            .filter(AiCalibrationFindingSource::missingReflectionMetadata)
            .count();
        return new AiCalibrationEvidence.ExecutionEvidence(
            result.executionId(),
            result.evalCaseId(),
            result.modelProfileId(),
            result.responseGuardStatus(),
            result.controlledDeliveryStatus(),
            guard == null ? List.of() : parseReasonCodes(guard.reasonCodes()),
            guard == null ? null : guard.detectorVersion(),
            guard == null ? 0 : guard.findingCount(),
            findings.size(),
            missingMetadataCount,
            groups
        );
    }

    private List<String> readinessReasons(
        List<AiCalibrationEvidence.ExecutionEvidence> executions,
        int guardCount,
        int expectedGuardCount
    ) {
        Set<String> reasons = new LinkedHashSet<>();
        if (guardCount != expectedGuardCount) reasons.add("RESPONSE_GUARD_EVIDENCE_MISSING");
        if (executions.stream().anyMatch(item -> item.missingReflectionMetadataCount() > 0)) {
            reasons.add("REFLECTION_METADATA_MISSING");
        }
        if (executions.stream().anyMatch(item -> item.findingCount() != item.observedFindingCount())) {
            reasons.add("FINDING_COUNT_MISMATCH");
        }
        return List.copyOf(reasons);
    }

    private List<String> parseReasonCodes(String value) {
        if (value == null || value.isBlank()) return List.of();
        return java.util.Arrays.stream(value.split(","))
            .map(String::trim)
            .filter(reason -> !reason.isEmpty())
            .distinct()
            .sorted()
            .toList();
    }

    private record FindingGroupKey(
        String findingType,
        String sourceDataClass,
        String transformStrategy,
        String fieldTreatment
    ) implements Comparable<FindingGroupKey> {
        private static FindingGroupKey from(AiCalibrationFindingSource source) {
            return new FindingGroupKey(
                source.findingType(), source.sourceDataClass(), source.transformStrategy(), source.fieldTreatment()
            );
        }

        @Override
        public int compareTo(FindingGroupKey other) {
            return Comparator.comparing(FindingGroupKey::findingType, Comparator.nullsFirst(String::compareTo))
                .thenComparing(FindingGroupKey::sourceDataClass, Comparator.nullsFirst(String::compareTo))
                .thenComparing(FindingGroupKey::transformStrategy, Comparator.nullsFirst(String::compareTo))
                .thenComparing(FindingGroupKey::fieldTreatment, Comparator.nullsFirst(String::compareTo))
                .compare(this, other);
        }
    }
}
