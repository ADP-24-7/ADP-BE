package com.adp.gateway.ai.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import com.adp.gateway.ai.domain.*;
import com.adp.gateway.context.application.CanonicalValueHasher;
import com.adp.gateway.context.domain.CanonicalContext;
import com.adp.gateway.context.domain.CanonicalContextField;
import com.adp.gateway.decision.domain.FinalAction;
import com.adp.gateway.decision.domain.RuntimeDecision;
import com.adp.gateway.egress.domain.DestinationProfile;
import com.adp.gateway.egress.domain.ProviderRequestPayload;
import com.adp.gateway.policy.domain.PolicySnapshot;
import com.adp.gateway.retrieval.domain.*;
import com.adp.gateway.runtime.application.RuntimeInputHasher;
import com.adp.gateway.transform.application.TransformEngine;
import com.adp.gateway.transform.application.TransformResolutionContext;
import com.adp.gateway.transform.domain.TransformFieldResult;
import com.adp.gateway.transform.domain.TransformResult;
import com.adp.gateway.transform.infrastructure.ProjectProvisionalTransformStrategyResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AiEvaluationContractServiceTests {
    final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    final AiEvaluationBundleCanonicalizer canonical = new AiEvaluationBundleCanonicalizer(mapper);
    final AiModelProfileCatalog models = new AiModelProfileCatalog(mapper, new CanonicalValueHasher());
    final AiEvaluationRunCatalog runs = new AiEvaluationRunCatalog(models, new RuntimeInputHasher(mapper), mapper, new CanonicalValueHasher());
    final AiEvaluationRunDefinition run = runs.find(AiEvaluationRunCatalog.BASELINE_RUN_ID).orElseThrow();
    final AiEvaluationContractPort port = mock(AiEvaluationContractPort.class);
    final ProjectProvisionalTransformStrategyResolver resolver = new ProjectProvisionalTransformStrategyResolver();
    final AiEvaluationContractService service = new AiEvaluationContractService(runs, models, port, canonical,
        mapper, null, null, null, null, null, null, resolver,
        Clock.fixed(Instant.parse("2026-09-10T00:00:00Z"), ZoneOffset.UTC));
    final CanonicalContext context = new CanonicalContext("canonical-context/v1", "test-context", "test-data",
        "customer_summary", "CUSTOMER_SUPPORT", "customer", "a".repeat(64),
        List.of(new CanonicalContextField("$.input.prompt", "request", "prompt", DataClass.BUSINESS_METADATA,
            AiEvaluationPrompt.TEXT, "b".repeat(64))), "c".repeat(64));
    final RetrievalResult retrieved = new RetrievalResult("test-data", "customer_summary", "CUSTOMER_SUPPORT",
        "customer", "customer-100", "profile_customer_summary_support", 1, List.of(), List.of(), List.of());

    PolicySnapshot policy() {
        var value = mock(PolicySnapshot.class);
        when(value.policyVersion()).thenReturn("be-runtime-policy/0.0.0");
        when(value.snapshotDigest()).thenReturn(run.policySnapshotDigest());
        return value;
    }
    DestinationProfile destination(AiModelProfile model) {
        var value = mock(DestinationProfile.class);
        when(value.providerProfileId()).thenReturn(model.profileId());
        when(value.profileDigest()).thenReturn(model.destinationProfileDigest());
        when(value.contractVersion()).thenReturn("nvidia-nim-chat-completions/2026-09-07");
        when(value.schemaVersion()).thenReturn("ai-provider-response/v1");
        when(value.tenantId()).thenReturn("tenant_local_ai_evaluation");
        when(value.region()).thenReturn("NVIDIA_HOSTED");
        when(value.retentionPolicy()).thenReturn("PROVIDER_CONTROLLED");
        when(value.fieldContracts()).thenReturn(List.of());
        when(value.allowedBindings()).thenReturn(List.of());
        return value;
    }
    AiEvaluationContractSnapshot snapshot(AiModelProfile model) {
        return service.snapshot(run, model, retrieved, context, policy(), destination(model));
    }

    @Test void frozenRetrievalDateIsReusedWhenExecutionDateChanges() {
        var model = models.profiles().getFirst();
        var frozenDate = LocalDate.of(2026, 9, 9);
        var frozen = service.snapshot(run, model, retrieved, context, policy(), destination(model), frozenDate);
        when(port.load(run.evaluationRunId())).thenReturn(Optional.of(frozen));

        assertThat(service.retrievalAsOfDate(run.evaluationRunId())).isEqualTo(frozenDate);
        assertThat(service.snapshot(run, model, retrieved, context, policy(), destination(model), frozenDate))
            .isEqualTo(frozen);
        assertThat(service.snapshot(run, model, retrieved, context, policy(), destination(model)))
            .isNotEqualTo(frozen);
    }

    @Test void differentRuntimeRetrievalDateFailsClosed() {
        var model = models.profiles().getFirst();
        var frozen = service.snapshot(run, model, retrieved, context, policy(), destination(model),
            LocalDate.of(2026, 9, 9));
        when(port.load(run.evaluationRunId())).thenReturn(Optional.of(frozen));
        var reference = new AiEvaluationReference(run.evaluationRunId(), AiEvaluationRunCatalog.BASELINE_CASE_ID,
            null, null, null, null);

        assertThatThrownBy(() -> service.validateAndBind(
            "test-date-drift", reference, null, null, null, null, null, null, null,
            LocalDate.of(2026, 9, 10)
        )).isInstanceOfSatisfying(AiEvaluationRunMismatchException.class, exception ->
            assertThat(exception.reasonCode()).isEqualTo("AI_RETRIEVAL_AS_OF_DATE_MISMATCH")
        );
        verify(port, never()).bind(anyString(), anyString(), anyString(), anyString(), anyString(),
            anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test void allThreeModelsShareFixedConditionsButHaveDistinctProfiles() throws Exception {
        var snapshots = models.profiles().stream().map(this::snapshot).toList();
        assertThat(snapshots.stream().map(AiEvaluationContractSnapshot::fixedConditionsDigest).distinct()).hasSize(1);
        assertThat(models.profiles().stream().map(AiModelProfile::modelProfileDigest).distinct()).hasSize(3);
        assertThat(snapshots.getFirst().fixedConditions().path("seed_control").asText()).isEqualTo("NOT_CONFIGURABLE");
        assertThat(snapshots.getFirst().fixedConditions().path("reasoning_control").asText()).isEqualTo("NOT_CONFIGURABLE");
        var path = java.nio.file.Path.of("build", "test-contract-snapshot.json");
        java.nio.file.Files.createDirectories(path.getParent());
        java.nio.file.Files.writeString(path, mapper.writeValueAsString(snapshots.getFirst()));
    }

    @ParameterizedTest @ValueSource(strings = {"prompt_version", "prompt_snapshot_digest", "policy_version",
        "policy_snapshot_digest", "transform_version", "rag_mode", "rag_version", "dataset_version",
        "dataset_digest", "temperature", "max_tokens", "case_set_version"})
    void anyFrozenConditionChangesTheDigest(String field) {
        var frozen = snapshot(models.profiles().getFirst());
        ObjectNode changed = (ObjectNode) frozen.fixedConditions();
        if (field.equals("temperature")) changed.put(field, 0.5);
        else if (field.equals("max_tokens")) changed.put(field, 1024);
        else changed.put(field, "changed");
        assertThat(canonical.digest(changed)).isNotEqualTo(frozen.fixedConditionsDigest());
        assertThat(canonical.digest(frozen.fixedConditions())).isEqualTo(frozen.fixedConditionsDigest());
    }

    @Test void missingFreezeStopsExecution() {
        when(port.load(run.evaluationRunId())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.required(run.evaluationRunId())).isInstanceOf(AiEvaluationRunMismatchException.class);
    }

    @Test void missingVersionCannotBeConstructed() {
        var frozen = snapshot(models.profiles().getFirst());
        ObjectNode fields = (ObjectNode) frozen.fixedConditions(); fields.remove("prompt_version");
        assertThatThrownBy(() -> new AiEvaluationContractSnapshot(fields, canonical.digest(fields)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void policyOrModelMismatchFailsClosed() {
        var model = models.profiles().getFirst();
        var wrongPolicy = policy(); when(wrongPolicy.snapshotDigest()).thenReturn("sha256:" + "f".repeat(64));
        assertThatThrownBy(() -> service.snapshot(run, model, retrieved, context, wrongPolicy, destination(model)))
            .isInstanceOf(AiEvaluationRunMismatchException.class);
        var wrongModel = new AiModelProfile(model.profileId(), model.profileVersion(), "unapproved/model", model.modelVersion(),
            model.providerConnectionProfileId(), model.modelProfileDigest(), model.destinationProfileId(),
            model.destinationProfileVersion(), model.destinationProfileDigest(), model.maxTokens(), model.temperature());
        assertThatThrownBy(() -> service.snapshot(run, wrongModel, retrieved, context, policy(), destination(model)))
            .isInstanceOf(AiEvaluationRunMismatchException.class);
    }

    @ParameterizedTest @ValueSource(strings = {"temperature", "max_tokens", "stream", "messages", "seed", "reasoning_effort",
        "contract:prompt_version", "contract:policy_version", "contract:transform_version", "contract:rag_version",
        "contract:dataset_digest", "contract:retrieved_context_digest", "case"})
    void bindsValidatedExecutionAndRejectsMapperSamplingDrift(String changedField) throws Exception {
        var model = models.profiles().getFirst();
        var frozen = snapshot(model);
        when(port.load(run.evaluationRunId())).thenReturn(Optional.of(frozen));
        var reference = runs.resolve(new AiEvaluationReference(run.evaluationRunId(), AiEvaluationRunCatalog.BASELINE_CASE_ID,
            null, null, null, null), Map.of("prompt", AiEvaluationPrompt.TEXT));
        var decision = mock(RuntimeDecision.class);
        when(decision.policyVersion()).thenReturn("be-runtime-policy/0.0.0");
        when(decision.snapshotDigest()).thenReturn(run.policySnapshotDigest());
        when(decision.finalAction()).thenReturn(FinalAction.TRANSFORM);
        when(decision.decisionId()).thenReturn("test-decision");
        var instruction = resolver.resolve(new TransformResolutionContext("customer_summary", "CUSTOMER_SUPPORT",
            model.profileId(), policy().policyVersion(), run.policySnapshotDigest(), DataClass.BUSINESS_METADATA, "$.input.prompt"));
        var transformed = new TransformResult("test-transform", true, "APPLIED", "d".repeat(64), List.of(
            new TransformFieldResult("$.input.prompt", "request", "prompt", DataClass.BUSINESS_METADATA,
                instruction.strategy(), instruction.strategyVersion(), instruction.keyVersion(), instruction.mappingVersion(),
                TransformEngine.instructionDigest(instruction), "b".repeat(64), "e".repeat(64), null, AiEvaluationPrompt.TEXT)));
        Map<String, Object> payload = new java.util.TreeMap<>(Map.of("model", model.modelId(), "messages",
            AiEvaluationPrompt.messages(mapper.writeValueAsString(Map.of("$.input.prompt", AiEvaluationPrompt.TEXT))),
            "temperature", 0.0, "max_tokens", 512, "stream", false));
        var request = new ProviderRequestPayload("test-request", "test-outbound", model.profileId(), "v1",
            canonical.digest(payload).substring(7), 1, payload);
        service.validateAndBind("test-execution", reference, retrieved, context, policy(), decision, transformed, destination(model), request);
        verify(port).bind(eq("test-execution"), eq(run.evaluationRunId()), eq(reference.evalCaseId()), eq(frozen.fixedConditionsDigest()),
            eq(model.modelProfileDigest()), eq("test-decision"), eq("test-transform"), eq("test-outbound"), anyString(), anyString());
        if (changedField.startsWith("contract:")) {
            ObjectNode drift = (ObjectNode) frozen.fixedConditions();
            drift.put(changedField.substring(9), "changed");
            when(port.load(run.evaluationRunId())).thenReturn(Optional.of(new AiEvaluationContractSnapshot(
                drift, canonical.digest(drift), frozen.modelProfiles())));
            assertThatThrownBy(() -> service.validateAndBind("test-drift", reference, retrieved, context,
                policy(), decision, transformed, destination(model), request)).isInstanceOf(AiEvaluationRunMismatchException.class);
            verify(port, times(1)).bind(anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString(), anyString());
            return;
        }
        if (changedField.equals("case")) {
            var wrongCase = new AiEvaluationReference(run.evaluationRunId(), "unregistered", null,
                reference.evaluationContractDigest(), reference.expectedInputDigest(), reference.actualInputDigest());
            assertThatThrownBy(() -> service.validateAndBind("test-case", wrongCase, retrieved, context,
                policy(), decision, transformed, destination(model), request)).isInstanceOf(AiEvaluationRunMismatchException.class);
            return;
        }
        payload.put(changedField, 0.7);
        var changed = new ProviderRequestPayload("test-request", "test-outbound", model.profileId(), "v1", "d".repeat(64), 1, payload);
        assertThatThrownBy(() -> service.validateAndBind("test-execution", reference, retrieved, context,
            policy(), decision, transformed, destination(model), changed)).isInstanceOf(AiEvaluationRunMismatchException.class);
    }
}
