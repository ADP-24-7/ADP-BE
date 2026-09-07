package com.adp.gateway.ai.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import com.adp.gateway.ai.domain.AiEvaluationBundleSource;
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
        new GatewayObservability(meterRegistry)
    );

    private List<AiEvaluationBundleSource> completeRows;

    @BeforeEach
    void setUp() {
        completeRows = models.profiles().stream().map(this::source).toList();
        when(port.load(
            AiEvaluationRunCatalog.BASELINE_RUN_ID, "institution_local", Set.of("customer_summary"), 10_001
        ))
            .thenReturn(completeRows);
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

        assertThat(bundle.failureSummary().total()).isEqualTo(3);
        assertThat(bundle.failureSummary().failed()).isEqualTo(1);
        assertThat(bundle.failureSummary().notAttempted()).isEqualTo(1);
        assertThat(bundle.failureSummary().sentUnknown()).isEqualTo(1);
        assertThat(bundle.failureSummary().byErrorCategory())
            .containsEntry("CONNECTION_CONFIGURATION", 1)
            .containsEntry("TRANSPORT", 1);
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
