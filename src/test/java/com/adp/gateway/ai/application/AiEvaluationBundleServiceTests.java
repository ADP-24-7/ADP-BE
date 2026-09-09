package com.adp.gateway.ai.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.adp.gateway.ai.domain.AiEvaluationCaseDefinition;
import com.adp.gateway.ai.domain.AiEvaluationBundleSource;
import com.adp.gateway.ai.domain.AiEvaluationRunDefinition;
import com.adp.gateway.ai.domain.AiModelProfile;
import com.adp.gateway.auth.domain.AdpRole;
import com.adp.gateway.auth.domain.AuthPrincipal;
import com.adp.gateway.auth.domain.PrincipalType;
import com.adp.gateway.context.application.CanonicalValueHasher;
import com.adp.gateway.observability.GatewayObservability;
import com.adp.gateway.runtime.application.RuntimeInputHasher;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AiEvaluationBundleServiceTests {
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final CanonicalValueHasher hasher = new CanonicalValueHasher();
    private final AiModelProfileCatalog models = new AiModelProfileCatalog(objectMapper, hasher);
    private final AiEvaluationRunCatalog runs = new AiEvaluationRunCatalog(
        models, new RuntimeInputHasher(objectMapper), objectMapper, hasher
    );
    private final AiEvaluationBundlePort port = mock(AiEvaluationBundlePort.class);
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final AiEvaluationBundleService service = new AiEvaluationBundleService(
        port, runs, models, new AiEvaluationBundleCanonicalizer(objectMapper),
        new GatewayObservability(meterRegistry), Clock.fixed(Instant.parse("2026-09-07T00:00:02Z"), ZoneOffset.UTC)
    );

    private List<AiEvaluationBundleSource> completeRows;

    @Test
    void exportsFrozenContractForIndependentDaValidation() throws Exception {
        var fixture = new AiEvaluationContractServiceTests();
        var snapshot = fixture.snapshot(fixture.models.profiles().getFirst());
        var contracts = mock(AiEvaluationContractPort.class);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "contractPort", contracts);
        var bindings = new ArrayList<Map<String, Object>>();
        for (var row : completeRows) {
            String decisionId = "decision-" + row.executionId();
            when(row.decisionId()).thenReturn(decisionId);
            when(row.providerHttpStatus()).thenReturn(null);
            when(row.attemptElapsedMillis()).thenReturn(null);
            when(row.inputTokens()).thenReturn(null);
            when(row.outputTokens()).thenReturn(null);
            when(row.totalTokens()).thenReturn(null);
            when(row.transformExecutionId()).thenReturn("test-transform");
            when(row.outboundPayloadId()).thenReturn("test-outbound");
            when(row.outboundGuardStatus()).thenReturn("PASSED");
            when(row.providerRequestDigest()).thenReturn("sha256:" + "a".repeat(64));
            bindings.add(Map.ofEntries(
                Map.entry("execution_id", row.executionId()), Map.entry("evaluation_run_id", row.evaluationRunId()),
                Map.entry("eval_case_id", row.evalCaseId()), Map.entry("fixed_conditions_digest", snapshot.fixedConditionsDigest()),
                Map.entry("model_profile_digest", row.profileDigest()), Map.entry("decision_id", row.decisionId()),
                Map.entry("transform_execution_id", "test-transform"), Map.entry("outbound_payload_id", "test-outbound"),
                Map.entry("outbound_guard_status", "PASSED"), Map.entry("provider_request_digest", row.providerRequestDigest()),
                Map.entry("provider_input_digest", "sha256:" + "b".repeat(64))));
        }
        when(contracts.evidence(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyList()))
            .thenReturn(Map.of("snapshot", snapshot, "bindings", bindings));
        var bundle = service.export(principal(), AiEvaluationRunCatalog.BASELINE_RUN_ID);
        assertThat(bundle.manifest().schemaVersion()).isEqualTo("adp-ai-evaluation-bundle/v2");
        // Test-only MOCK artifact, never runtime evidence or a measurement.
        java.nio.file.Files.writeString(java.nio.file.Path.of("build", "test-contract-bundle.json"),
            objectMapper.copy().disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .writeValueAsString(bundle));
    }

    @BeforeEach
    void setUp() {
        completeRows = models.profiles().stream().map(this::source).toList();
        when(port.load(
            AiEvaluationRunCatalog.BASELINE_RUN_ID, "institution_local", Set.of("customer_summary"), 10_001
        ))
            .thenReturn(completeRows);
        when(port.countStored(
            AiEvaluationRunCatalog.BASELINE_RUN_ID, "institution_local", Set.of("customer_summary")
        )).thenReturn(3L);
    }

    @Test
    void rejectsIncompleteCaseModelMatrix() {
        when(port.load(
            AiEvaluationRunCatalog.BASELINE_RUN_ID, "institution_local", Set.of("customer_summary"), 10_001
        ))
            .thenReturn(completeRows.subList(0, 2));

        assertReason("AI_EVALUATION_BUNDLE_INCOMPLETE");
    }

    @Test
    void reportsCaseModelReadinessWithoutExportingRawData() {
        var readiness = service.readiness(principal(), AiEvaluationRunCatalog.BASELINE_RUN_ID);

        assertThat(readiness.status()).isEqualTo(
            com.adp.gateway.ai.domain.AiEvaluationRunReadiness.Status.READY
        );
        assertThat(readiness.bundleAvailable()).isTrue();
        assertThat(readiness.expectedExecutionCount()).isEqualTo(3);
        assertThat(readiness.storedExecutionCount()).isEqualTo(3);
        assertThat(readiness.observedExecutionCount()).isEqualTo(3);
        assertThat(readiness.completeEvidenceCount()).isEqualTo(3);
        assertThat(readiness.missingExecutionCount()).isZero();
        assertThat(readiness.unexpectedExecutionCount()).isZero();
        assertThat(readiness.caseModels())
            .allSatisfy(entry -> {
                assertThat(entry.executionId()).startsWith("exec-");
                assertThat(entry.evidenceStatus()).isEqualTo("COMPLETE");
            });
    }

    @Test
    void reportsNotStartedAndIncompleteRuns() {
        when(port.load(
            AiEvaluationRunCatalog.BASELINE_RUN_ID, "institution_local", Set.of("customer_summary"), 10_001
        )).thenReturn(List.of()).thenReturn(completeRows.subList(0, 2));
        when(port.countStored(
            AiEvaluationRunCatalog.BASELINE_RUN_ID, "institution_local", Set.of("customer_summary")
        )).thenReturn(0L).thenReturn(2L);

        var notStarted = service.readiness(principal(), AiEvaluationRunCatalog.BASELINE_RUN_ID);
        var incomplete = service.readiness(principal(), AiEvaluationRunCatalog.BASELINE_RUN_ID);

        assertThat(notStarted.status()).isEqualTo(
            com.adp.gateway.ai.domain.AiEvaluationRunReadiness.Status.NOT_STARTED
        );
        assertThat(notStarted.bundleAvailable()).isFalse();
        assertThat(notStarted.storedExecutionCount()).isZero();
        assertThat(notStarted.missingExecutionCount()).isEqualTo(3);
        assertThat(incomplete.status()).isEqualTo(
            com.adp.gateway.ai.domain.AiEvaluationRunReadiness.Status.INCOMPLETE
        );
        assertThat(incomplete.bundleAvailable()).isFalse();
        assertThat(incomplete.observedExecutionCount()).isEqualTo(2);
        assertThat(incomplete.storedExecutionCount()).isEqualTo(2);
        assertThat(incomplete.missingExecutionCount()).isEqualTo(1);
        assertThat(incomplete.caseModels()).anyMatch(entry -> entry.executionId() == null);
    }

    @Test
    void reportsIntegrityMismatchBeforeBundleExport() {
        when(completeRows.getFirst().datasetDigest()).thenReturn("sha256:" + "f".repeat(64));

        var readiness = service.readiness(principal(), AiEvaluationRunCatalog.BASELINE_RUN_ID);

        assertThat(readiness.status()).isEqualTo(
            com.adp.gateway.ai.domain.AiEvaluationRunReadiness.Status.PROVENANCE_MISMATCH
        );
        assertThat(readiness.bundleAvailable()).isFalse();
    }

    @Test
    void rejectsDuplicateCaseModelProjection() {
        when(port.load(
            AiEvaluationRunCatalog.BASELINE_RUN_ID, "institution_local", Set.of("customer_summary"), 10_001
        )).thenReturn(List.of(
            completeRows.get(0), completeRows.get(0), completeRows.get(1), completeRows.get(2)
        ));

        assertReason("AI_EVALUATION_BUNDLE_INCOMPLETE");
    }

    @Test
    void rejectsEvidenceThatDoesNotMatchAuthoritativeRunProvenance() {
        when(completeRows.getFirst().datasetDigest())
            .thenReturn("sha256:" + "f".repeat(64));

        assertReason("AI_EVALUATION_BUNDLE_PROVENANCE_MISMATCH");
    }

    @Test
    void rejectsEvidenceThatDoesNotMatchAuthoritativeModelProfile() {
        when(completeRows.getFirst().profileDigest())
            .thenReturn("sha256:" + "e".repeat(64));

        assertReason("AI_EVALUATION_BUNDLE_MODEL_MISMATCH");
    }

    @Test
    void summarizesFailureEvidenceWithoutChangingMatrixCompleteness() {
        AiEvaluationBundleSource failed = completeRows.getFirst();
        when(failed.providerStatus()).thenReturn("FAILED");
        when(failed.measurementType()).thenReturn("NOT_ATTEMPTED");
        when(failed.errorCategory()).thenReturn("CONNECTION_CONFIGURATION");
        AiEvaluationBundleSource uncertain = completeRows.get(1);
        when(uncertain.providerStatus()).thenReturn("SENT_UNKNOWN");
        when(uncertain.errorCategory()).thenReturn("TRANSPORT");

        var bundle = service.export(principal(), AiEvaluationRunCatalog.BASELINE_RUN_ID);

        assertThat(bundle.manifest().generatedAt())
            .isEqualTo(OffsetDateTime.parse("2026-09-07T00:00:02Z"));
        assertThat(bundle.manifest().executionCutoffAt())
            .isEqualTo(OffsetDateTime.parse("2026-09-07T00:00:01Z"));
        assertThat(bundle.failureSummary().evaluatedExecutionCount()).isEqualTo(3);
        assertThat(bundle.failureSummary().failed()).isEqualTo(1);
        assertThat(bundle.failureSummary().notAttempted()).isEqualTo(1);
        assertThat(bundle.failureSummary().sentUnknown()).isEqualTo(1);
        assertThat(bundle.failureSummary().byErrorCategory())
            .containsEntry("CONNECTION_CONFIGURATION", 1)
            .containsEntry("TRANSPORT", 1);
    }

    @Test
    void digestIsIndependentOfPortRowOrdering() {
        var shuffled = new ArrayList<>(completeRows);
        Collections.reverse(shuffled);
        when(port.load(
            AiEvaluationRunCatalog.BASELINE_RUN_ID, "institution_local", Set.of("customer_summary"), 10_001
        )).thenReturn(shuffled).thenReturn(completeRows);

        var first = service.export(principal(), AiEvaluationRunCatalog.BASELINE_RUN_ID);
        var second = service.export(principal(), AiEvaluationRunCatalog.BASELINE_RUN_ID);

        assertThat(first.manifest().contentDigest()).isEqualTo(second.manifest().contentDigest());
        assertThat(first.caseResults().stream().map(result -> result.executionId()).toList())
            .isEqualTo(second.caseResults().stream().map(result -> result.executionId()).toList());
    }

    @Test
    void inaccessibleOversizedRunDoesNotDiscloseSize() {
        String runId = "oversized-run";
        AiEvaluationRunDefinition oversizedRun = mock(AiEvaluationRunDefinition.class);
        Map<String, AiEvaluationCaseDefinition> oversizedCases = new AbstractMap<>() {
            @Override
            public Set<Entry<String, AiEvaluationCaseDefinition>> entrySet() {
                return Set.of();
            }

            @Override
            public int size() {
                return AiEvaluationBundleService.MAX_EXECUTION_COUNT + 1;
            }
        };
        when(oversizedRun.cases()).thenReturn(oversizedCases);
        when(oversizedRun.modelProfileIds()).thenReturn(Set.of("model"));
        AiEvaluationRunCatalog oversizedRuns = mock(AiEvaluationRunCatalog.class);
        when(oversizedRuns.find(runId)).thenReturn(Optional.of(oversizedRun));
        AiEvaluationBundlePort scopedPort = mock(AiEvaluationBundlePort.class);
        when(scopedPort.load(runId, "institution_local", Set.of("customer_summary"), 10_001))
            .thenReturn(List.of());
        var scopedService = new AiEvaluationBundleService(
            scopedPort, oversizedRuns, models, new AiEvaluationBundleCanonicalizer(objectMapper),
            new GatewayObservability(new SimpleMeterRegistry()), Clock.systemUTC()
        );

        assertThatThrownBy(() -> scopedService.export(principal(), runId))
            .isInstanceOf(AiEvaluationBundleNotFoundException.class);
    }

    @Test
    void rejectsLatestPartialEvidence() {
        when(completeRows.getFirst().evidenceStatus()).thenReturn("PARTIAL");

        assertReason("AI_EVALUATION_BUNDLE_INCOMPLETE");
    }

    private void assertReason(String reasonCode) {
        assertThatThrownBy(() -> service.export(principal(), AiEvaluationRunCatalog.BASELINE_RUN_ID))
            .isInstanceOf(AiEvaluationBundleIntegrityException.class)
            .satisfies(exception -> assertThat(
                ((AiEvaluationBundleIntegrityException) exception).reasonCode()
            ).isEqualTo(reasonCode));
    }

    private AiEvaluationBundleSource source(AiModelProfile profile) {
        var run = runs.find(AiEvaluationRunCatalog.BASELINE_RUN_ID).orElseThrow();
        var evaluationCase = run.cases().get(AiEvaluationRunCatalog.BASELINE_CASE_ID);
        AiEvaluationBundleSource source = mock(AiEvaluationBundleSource.class);
        when(source.executionId()).thenReturn("exec-" + profile.profileId());
        when(source.evaluationRunId()).thenReturn(run.evaluationRunId());
        when(source.evaluationRunVersion()).thenReturn(run.runVersion());
        when(source.evaluationContractDigest()).thenReturn(run.contractDigest());
        when(source.evalCaseId()).thenReturn(evaluationCase.caseId());
        when(source.expectedInputDigest()).thenReturn(evaluationCase.expectedInputDigest());
        when(source.actualInputDigest()).thenReturn(evaluationCase.expectedInputDigest());
        when(source.datasetId()).thenReturn(run.datasetId());
        when(source.datasetVersion()).thenReturn(run.datasetVersion());
        when(source.datasetDigest()).thenReturn(run.datasetDigest());
        when(source.policySnapshotDigest()).thenReturn(run.policySnapshotDigest());
        when(source.profileId()).thenReturn(profile.profileId());
        when(source.profileVersion()).thenReturn(profile.profileVersion());
        when(source.profileDigest()).thenReturn(profile.modelProfileDigest());
        when(source.providerModelId()).thenReturn(profile.modelId());
        when(source.providerModelVersion()).thenReturn(profile.modelVersion());
        when(source.connectionProfileId()).thenReturn(profile.providerConnectionProfileId());
        when(source.maxTokens()).thenReturn(profile.maxTokens());
        when(source.temperature()).thenReturn(profile.temperature());
        when(source.samplingProfileVersion()).thenReturn(profile.profileVersion());
        when(source.destinationProfileDigest()).thenReturn(profile.destinationProfileDigest());
        when(source.runtimeStatus()).thenReturn("COMPLETED");
        when(source.finalAction()).thenReturn("TRANSFORM");
        when(source.responseGuardStatus()).thenReturn("PASSED");
        when(source.controlledDeliveryStatus()).thenReturn("DELIVERED");
        when(source.providerStatus()).thenReturn("ACKNOWLEDGED");
        when(source.errorCategory()).thenReturn("NONE");
        when(source.evidenceStatus()).thenReturn("COMPLETE");
        when(source.measurementType()).thenReturn("MOCK");
        when(source.fullResponseLatencyMillis()).thenReturn(0L);
        when(source.tokenUsageStatus()).thenReturn("NOT_PROVIDED");
        when(source.createdAt()).thenReturn(OffsetDateTime.of(2026, 9, 7, 0, 0, 0, 0, ZoneOffset.UTC));
        when(source.updatedAt()).thenReturn(OffsetDateTime.of(2026, 9, 7, 0, 0, 1, 0, ZoneOffset.UTC));
        return source;
    }

    private AuthPrincipal principal() {
        return new AuthPrincipal(
            "bundle-exporter", PrincipalType.USER, "Bundle Exporter", "institution_local",
            false, Set.of("customer_summary"), Set.of(AdpRole.PRIVILEGED_OPERATOR)
        );
    }
}
