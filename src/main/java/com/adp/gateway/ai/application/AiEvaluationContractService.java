package com.adp.gateway.ai.application;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.adp.gateway.ai.domain.*;
import com.adp.gateway.auth.domain.AuthPrincipal;
import com.adp.gateway.auth.domain.SubjectRef;
import com.adp.gateway.context.application.CanonicalContextBuilder;
import com.adp.gateway.context.domain.CanonicalContext;
import com.adp.gateway.dataaccess.application.DataAccessRequest;
import com.adp.gateway.decision.domain.RuntimeDecision;
import com.adp.gateway.egress.application.DestinationProfilePort;
import com.adp.gateway.egress.domain.DestinationProfile;
import com.adp.gateway.egress.domain.ProviderRequestPayload;
import com.adp.gateway.policy.domain.PolicySnapshotPort;
import com.adp.gateway.policy.application.RuntimePolicyContextFactory;
import com.adp.gateway.policy.domain.PolicySelectionContext;
import com.adp.gateway.policy.domain.PolicySnapshot;
import com.adp.gateway.retrieval.application.RetrievalService;
import com.adp.gateway.retrieval.domain.RetrievalResult;
import com.adp.gateway.transform.application.TransformEngine;
import com.adp.gateway.transform.application.TransformResolutionContext;
import com.adp.gateway.transform.application.TransformStrategyResolver;
import com.adp.gateway.transform.domain.TransformResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.security.access.AccessDeniedException;

@Service
public class AiEvaluationContractService {
    public static final String VERSION = "ai-evaluation-contract/1.0.0";
    private final AiEvaluationRunCatalog runs;
    private final AiModelProfileCatalog models;
    private final AiEvaluationContractPort store;
    private final AiEvaluationBundleCanonicalizer canonical;
    private final ObjectMapper mapper;
    private final RetrievalService retrieval;
    private final CanonicalContextBuilder contexts;
    private final AiCanonicalContextBuilder prompts;
    private final DestinationProfilePort destinations;
    private final PolicySnapshotPort policies;
    private final RuntimePolicyContextFactory policyContexts;
    private final TransformStrategyResolver transforms;
    private final Clock clock;

    public AiEvaluationContractService(AiEvaluationRunCatalog runs, AiModelProfileCatalog models,
        AiEvaluationContractPort store, AiEvaluationBundleCanonicalizer canonical, ObjectMapper mapper,
        RetrievalService retrieval, CanonicalContextBuilder contexts, AiCanonicalContextBuilder prompts,
        DestinationProfilePort destinations, PolicySnapshotPort policies,
        RuntimePolicyContextFactory policyContexts, TransformStrategyResolver transforms, Clock clock) {
        this.runs = runs; this.models = models; this.store = store; this.canonical = canonical;
        this.mapper = mapper; this.retrieval = retrieval; this.contexts = contexts; this.prompts = prompts;
        this.destinations = destinations; this.policies = policies; this.policyContexts = policyContexts;
        this.transforms = transforms; this.clock = clock;
    }

    public AiEvaluationContractSnapshot read(AuthPrincipal principal, String runId) {
        authorize(principal);
        return required(runId);
    }

    /** Explicit server-side preparation: performs approved read-only retrieval, never a provider call. */
    public AiEvaluationContractSnapshot freeze(AuthPrincipal principal, String runId) {
        authorize(principal);
        var run = run(runId);
        LocalDate retrievalAsOfDate = LocalDate.now(clock);
        // The current freezer prepares one case per immutable run. Never silently ignore new cases.
        require(run.cases().size() == 1, "AI_CASE_SET_NOT_SUPPORTED");
        var evaluationCase = run.cases().values().iterator().next();
        var retrieved = retrieval.retrieve(new DataAccessRequest("evaluation-contract-freeze",
            "evaluation-contract-freeze", "customer_summary", "CUSTOMER_SUPPORT",
            new SubjectRef("customer", subjectId(evaluationCase.datasetRowRef())), retrievalAsOfDate));
        var context = prompts.merge(contexts.build(retrieved), Map.of("prompt", AiEvaluationPrompt.TEXT), null);
        AiEvaluationContractSnapshot candidate = null;
        for (var model : models.profiles()) {
            var destination = destinations.load(model.destinationProfileId(), OffsetDateTime.now(clock));
            var policyContext = policyContexts.from(context, List.of("AI_USE"), model.profileId(), "freeze");
            var policy = policies.load(new PolicySelectionContext(context.workloadId(), context.purpose(),
                model.profileId(), policyContext.processingContexts(), policyContext.runtimeDataClasses(),
                principal.institutionId(), destination.packType()));
            var current = snapshot(run, model, retrieved, context, policy, destination, retrievalAsOfDate);
            if (candidate != null) require(candidate.equals(current), "AI_CROSS_MODEL_CONDITIONS_MISMATCH");
            candidate = current;
        }
        require(candidate != null, "AI_MODEL_SET_EMPTY");
        return store.freeze(runId, candidate);
    }

    public AiEvaluationContractSnapshot required(String runId) {
        run(runId);
        var snapshot = store.load(runId).orElseThrow(() -> mismatch("AI_CONTRACT_NOT_FROZEN"));
        require(canonical.digest(snapshot.fixedConditions()).equals(snapshot.fixedConditionsDigest()),
            "AI_FIXED_CONDITIONS_DIGEST_MISMATCH");
        require(snapshot.fixedConditions().path("evaluation_contract_version").asText().equals(VERSION),
            "AI_CONTRACT_VERSION_MISMATCH");
        require(snapshot.fixedConditions().path("evaluation_run_id").asText().equals(runId), "AI_CONTRACT_RUN_MISMATCH");
        require(snapshot.modelProfiles().equals(modelSnapshots()), "AI_MODEL_SNAPSHOT_MISMATCH");
        return snapshot;
    }

    public void validateAndBind(String executionId, AiEvaluationReference reference, RetrievalResult retrieved,
        CanonicalContext context, PolicySnapshot policy, RuntimeDecision decision, TransformResult transformed,
        DestinationProfile destination, ProviderRequestPayload request) {
        validateAndBind(executionId, reference, retrieved, context, policy, decision, transformed,
            destination, request, retrievalAsOfDate(reference.evaluationRunId()));
    }

    public void validateAndBind(String executionId, AiEvaluationReference reference, RetrievalResult retrieved,
        CanonicalContext context, PolicySnapshot policy, RuntimeDecision decision, TransformResult transformed,
        DestinationProfile destination, ProviderRequestPayload request, LocalDate retrievalAsOfDate) {
        var run = run(reference.evaluationRunId());
        var frozen = required(run.evaluationRunId());
        require(frozenRetrievalAsOfDate(frozen).equals(retrievalAsOfDate),
            "AI_RETRIEVAL_AS_OF_DATE_MISMATCH");
        var model = models.findByProfileId(destination.providerProfileId())
            .orElseThrow(() -> mismatch("AI_MODEL_NOT_APPROVED"));
        var live = snapshot(run, model, retrieved, context, policy, destination, retrievalAsOfDate);
        require(frozen.equals(live), "AI_FIXED_CONDITIONS_MISMATCH");
        require(run.cases().containsKey(reference.evalCaseId())
            && run.contractDigest().equals(reference.evaluationContractDigest())
            && run.cases().get(reference.evalCaseId()).expectedInputDigest().equals(reference.actualInputDigest()),
            "AI_CASE_CONTRACT_MISMATCH");
        require(decision.policyVersion().equals(policy.policyVersion())
            && decision.snapshotDigest().equals(policy.snapshotDigest())
            && decision.finalAction().name().equals("TRANSFORM") && transformed.applied(),
            "AI_POLICY_TRANSFORM_MISMATCH");
        var actualInstructions = transformed.fields().stream().map(field ->
            Map.of("path", field.path(), "instruction_digest", field.instructionDigest())).toList();
        require(canonical.digest(actualInstructions).equals(frozen.fixedConditions().path("transform_version").asText()),
            "AI_TRANSFORM_RULESET_MISMATCH");
        // Mapper output, not caller-declared sampling, is checked before connector execution.
        var payload = mapper.valueToTree(request.payload());
        Map<String, Object> expectedFields = new TreeMap<>();
        transformed.fields().stream()
            .filter(field -> field.strategy() != com.adp.gateway.transform.domain.TransformStrategy.REMOVE)
            .forEach(field -> expectedFields.put(field.path(), field.transformedValue()));
        try {
            require(payload.path("messages").equals(mapper.valueToTree(
                AiEvaluationPrompt.messages(mapper.writeValueAsString(expectedFields)))), "AI_PROMPT_PAYLOAD_MISMATCH");
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw mismatch("AI_PROMPT_PAYLOAD_INVALID");
        }
        require(request.payload().keySet().equals(java.util.Set.of("model", "messages", "temperature", "max_tokens", "stream"))
            && payload.path("model").asText().equals(model.modelId())
            && payload.path("temperature").isNumber() && payload.path("temperature").asDouble(-1) == model.temperature()
            && payload.path("max_tokens").isIntegralNumber() && payload.path("max_tokens").asInt(-1) == model.maxTokens()
            && payload.path("stream").isBoolean() && !payload.path("stream").asBoolean(true)
            && !payload.has("seed") && !payload.has("reasoning") && !payload.has("reasoning_effort"),
            "AI_PROVIDER_CONFIG_MISMATCH");
        Map<String, Object> providerInput = new TreeMap<>(request.payload());
        providerInput.remove("model");
        store.bind(executionId, run.evaluationRunId(), reference.evalCaseId(), frozen.fixedConditionsDigest(),
            model.modelProfileDigest(), decision.decisionId(), transformed.transformExecutionId(),
            request.outboundPayloadId(), request.canonicalPayloadDigest(), canonical.digest(providerInput));
    }

    public AiEvaluationContractSnapshot snapshot(AiEvaluationRunDefinition run, AiModelProfile model,
        RetrievalResult retrieved, CanonicalContext context, PolicySnapshot policy, DestinationProfile destination) {
        return snapshot(run, model, retrieved, context, policy, destination, LocalDate.now(clock));
    }

    AiEvaluationContractSnapshot snapshot(AiEvaluationRunDefinition run, AiModelProfile model,
        RetrievalResult retrieved, CanonicalContext context, PolicySnapshot policy, DestinationProfile destination,
        LocalDate retrievalAsOfDate) {
        require(run.modelProfileIds().contains(model.profileId())
            && models.findByProfileId(model.profileId()).filter(model::equals).isPresent(), "AI_MODEL_NOT_APPROVED");
        var registered = run(run.evaluationRunId());
        require(registered.datasetVersion().equals(run.datasetVersion())
            && registered.datasetDigest().equals(run.datasetDigest()), "AI_DATASET_SNAPSHOT_MISMATCH");
        require(run.policySnapshotDigest().equals(policy.snapshotDigest()), "AI_POLICY_SNAPSHOT_MISMATCH");
        require(model.destinationProfileDigest().equals(destination.profileDigest()), "AI_DESTINATION_MISMATCH");
        require(context.workloadId().equals("customer_summary") && context.purpose().equals("CUSTOMER_SUPPORT")
            && retrieved.subjectType().equals("customer")
            && retrieved.subjectId().equals(subjectId(run.cases().values().iterator().next().datasetRowRef())),
            "AI_WORKLOAD_CASE_MISMATCH");
        var plans = context.fields().stream().map(field -> {
            var instruction = transforms.resolve(new TransformResolutionContext(context.workloadId(), context.purpose(),
                model.profileId(), policy.policyVersion(), policy.snapshotDigest(), field.dataClass(), field.path()));
            return Map.of("path", field.path(), "instruction_digest", TransformEngine.instructionDigest(instruction));
        }).toList();
        var retrievalConfig = Map.of("adapter_version", "jdbc-customer-summary/v1",
            "profile_id", retrieved.profileId(), "scopes", retrieved.datasetScopes(),
            "fields", retrieved.selectedFields(), "as_of_date", retrievalAsOfDate.toString());
        Map<String, Object> fixed = new TreeMap<>();
        fixed.put("evaluation_run_id", run.evaluationRunId());
        fixed.put("evaluation_contract_version", VERSION);
        fixed.put("workload", context.workloadId()); fixed.put("purpose_code", context.purpose());
        fixed.put("dataset_version", run.datasetVersion()); fixed.put("dataset_digest", run.datasetDigest());
        fixed.put("retrieved_context_digest", context.contextDigest());
        fixed.put("prompt_version", AiEvaluationPrompt.VERSION);
        fixed.put("prompt_snapshot_digest", canonical.digest(AiEvaluationPrompt.snapshot()));
        fixed.put("prompt_snapshot", AiEvaluationPrompt.snapshot());
        fixed.put("policy_version", policy.policyVersion()); fixed.put("policy_snapshot_digest", policy.snapshotDigest());
        fixed.put("transform_version", canonical.digest(plans));
        fixed.put("transform_snapshot", plans);
        fixed.put("transform_scope", "ai-evaluation:" + run.evaluationRunId());
        fixed.put("rag_mode", "PREDEFINED_RETRIEVAL"); fixed.put("rag_version", canonical.digest(retrievalConfig));
        fixed.put("retrieval_config", mapper.valueToTree(retrievalConfig));
        fixed.put("temperature", model.temperature()); fixed.put("max_tokens", model.maxTokens());
        fixed.put("stream", false); fixed.put("seed_control", "NOT_CONFIGURABLE");
        fixed.put("reasoning_control", "NOT_CONFIGURABLE");
        fixed.put("case_set_version", run.runVersion());
        fixed.put("cases", run.cases().values().stream().sorted(java.util.Comparator.comparing(AiEvaluationCaseDefinition::caseId)).toList());
        fixed.put("destination_contract_digest", canonical.digest(Map.of("contract_version", destination.contractVersion(),
            "schema_version", destination.schemaVersion(), "tenant", destination.tenantId(), "region", destination.region(),
            "retention", destination.retentionPolicy(), "training", destination.trainingUseAllowed(),
            "fields", destination.fieldContracts(), "bindings", destination.allowedBindings())));
        var tree = mapper.valueToTree(fixed);
        return new AiEvaluationContractSnapshot(tree, canonical.digest(tree), modelSnapshots());
    }

    public LocalDate retrievalAsOfDate(String runId) {
        return frozenRetrievalAsOfDate(required(runId));
    }

    private LocalDate frozenRetrievalAsOfDate(AiEvaluationContractSnapshot frozen) {
        String value = frozen.fixedConditions().path("retrieval_config").path("as_of_date").asText();
        try {
            return LocalDate.parse(value);
        } catch (java.time.format.DateTimeParseException exception) {
            throw mismatch("AI_RETRIEVAL_AS_OF_DATE_INVALID");
        }
    }

    private List<AiEvaluationBundle.ModelConfig> modelSnapshots() {
        return models.profiles().stream().sorted(java.util.Comparator.comparing(AiModelProfile::profileId))
            .map(model -> new AiEvaluationBundle.ModelConfig(model.profileId(), model.profileVersion(),
                model.modelProfileDigest(), model.modelId(), model.modelVersion(), model.providerConnectionProfileId(),
                model.maxTokens(), model.temperature(), model.profileVersion(), model.destinationProfileDigest(),
                model.destinationProfileId(), "NVIDIA")).toList();
    }

    private AiEvaluationRunDefinition run(String id) {
        return runs.find(id).orElseThrow(() -> mismatch("AI_EVALUATION_RUN_NOT_FOUND"));
    }
    private void authorize(AuthPrincipal principal) {
        if (!"institution_local".equals(principal.institutionId())
            || !(principal.workloadIds().contains("*") || principal.workloadIds().contains("customer_summary"))) {
            throw new AccessDeniedException("Evaluation contract scope denied");
        }
    }
    private String subjectId(String datasetRowRef) {
        if (AiEvaluationRunCatalog.DA_PROVENANCE_DATASET_ROW_REF.equals(datasetRowRef)) {
            return "da-customer-10832";
        }
        if ("synthetic:customer-100".equals(datasetRowRef)) {
            return "customer-100";
        }
        throw mismatch("AI_CASE_ROW_REF_NOT_SUPPORTED");
    }
    private static AiEvaluationRunMismatchException mismatch(String code) { return new AiEvaluationRunMismatchException(code); }
    private static void require(boolean value, String code) { if (!value) throw mismatch(code); }
}
