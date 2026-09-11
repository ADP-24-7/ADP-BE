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
    private static final String E2_REGULATORY_CORPUS_DIGEST =
        "sha256:a77ee367aed743c909c88ff446af72d0a1abc8d581c70819141f7567d96d8f9f";
    private static final String E2_SYNTHETIC_EGRESS_EVIDENCE_DIGEST =
        "sha256:ab100dde0147c22177b3e7842cf3dc69a8d52fe4ad435a75902ac291f59b2b2f";
    private static final String E2_TEMPORAL_PROVENANCE_DIGEST =
        "sha256:7deb467f14d4b054f7f6106d8c874185050173b41e8a1c0b3411de073f940e42";
    private static final List<String> E2_REGULATORY_EVIDENCE_IDS = List.of(
        "FSC-AI-GUIDELINE-2026-06", "FSS-AI-RMF-2026", "FSI-AI-SECURITY-2026-06", "PIPA-2026",
        "CREDIT-INFO-ACT-2026", "AI-BASIC-ACT-2026", "AI-BASIC-DECREE-2026", "EFSR-2026",
        "FSI-FRONTIER-AI-2026", "FSI-SAAS-GUIDE-2026"
    );
    private static final List<String> E2_REGULATORY_REQUIREMENT_REFS = List.of(
        "FSC-GOVERNANCE", "FSC-LEGALITY", "FSC-ASSISTIVE", "FSC-RELIABILITY",
        "FSC-FINANCIAL-STABILITY", "FSC-GOOD-FAITH", "FSC-SECURITY",
        "FSS-RMF-ACCOUNTABILITY", "FSS-RMF-RISK-IDENTIFY", "FSS-RMF-RESIDUAL-RISK",
        "FSS-RMF-DIFFERENTIATED-CONTROL", "FSS-RMF-MONITORING",
        "FSI-THREAT-MODEL", "FSI-INPUT-OUTPUT-FILTER", "FSI-DATA-LEAKAGE", "FSI-ASSET-INTEGRITY",
        "FSI-SUPPLY-CHAIN", "FSI-ACCESS-NETWORK-LOG", "FSI-SECURITY-VALIDATION",
        "PIPA-LAWFUL-PURPOSE", "PIPA-MINIMIZATION", "PIPA-PROVISION-OUTSOURCING",
        "PIPA-PSEUDONYMIZATION", "PIPA-OVERSEAS-TRANSFER", "PIPA-SECURITY-DISPOSAL",
        "PIPA-AUTOMATED-DECISION", "CREDIT-COLLECTION-USE", "CREDIT-SAFEGUARDS-RETENTION",
        "CREDIT-PROVISION-PURPOSE", "CREDIT-AUTOMATED-EVALUATION",
        "AI-ACT-GENERATIVE-TRANSPARENCY", "AI-ACT-HIGH-IMPACT-CHECK", "AI-ACT-HIGH-IMPACT-DUTIES",
        "AI-DECREE-HIGH-IMPACT-EVIDENCE", "EFSR-CLOUD-IMPORTANCE", "EFSR-CLOUD-SAFEGUARDS",
        "EFSR-NETWORK-BOUNDARY", "FSI-FRONTIER-ZERO-TRUST", "FSI-SAAS-EXCEPTION"
    );
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
        // E2 freezes a declared three-case set; legacy E1 runs retain their exact single-case contract.
        require(run.cases().size() == 1 || (isRegulatoryExperiment02(runId)
            && run.cases().size() == 3), "AI_CASE_SET_NOT_SUPPORTED");
        var evaluationCase = run.cases().values().stream()
            .min(java.util.Comparator.comparing(AiEvaluationCaseDefinition::caseId)).orElseThrow();
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
        require((isRegulatoryExperiment02(run.evaluationRunId())
                && fixedE1TransformProfileDigest().equals(frozen.fixedConditions().path("transform_version").asText()))
            || canonical.digest(actualInstructions).equals(frozen.fixedConditions().path("transform_version").asText()),
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
            && run.cases().values().stream().map(item -> subjectId(item.datasetRowRef())).anyMatch(retrieved.subjectId()::equals),
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
        fixed.put("retrieved_context_digest", isRegulatoryExperiment02(run.evaluationRunId())
            ? "PER_CASE_BOUND" : context.contextDigest());
        fixed.put("prompt_version", AiEvaluationPrompt.VERSION);
        fixed.put("prompt_snapshot_digest", canonical.digest(AiEvaluationPrompt.snapshot()));
        fixed.put("prompt_snapshot", AiEvaluationPrompt.snapshot());
        fixed.put("policy_version", policy.policyVersion()); fixed.put("policy_snapshot_digest", policy.snapshotDigest());
        boolean experiment02 = isRegulatoryExperiment02(run.evaluationRunId());
        fixed.put("transform_version", experiment02 ? fixedE1TransformProfileDigest() : canonical.digest(plans));
        fixed.put("transform_snapshot", experiment02
            ? List.of(Map.of("profile", "EXPERIMENT_01_FPG_TRANSFORM", "comparison", "DISABLED")) : plans);
        fixed.put("transform_scope", "ai-evaluation:" + run.evaluationRunId());
        fixed.put("rag_mode", "PREDEFINED_RETRIEVAL"); fixed.put("rag_version", canonical.digest(retrievalConfig));
        if (experiment02) {
            fixed.put("regulatory_corpus", "financial-regulatory-evidence/v2");
            fixed.put("regulatory_corpus_digest", E2_REGULATORY_CORPUS_DIGEST);
            fixed.put("regulatory_evidence_ids", E2_REGULATORY_EVIDENCE_IDS);
            fixed.put("regulatory_requirement_refs", E2_REGULATORY_REQUIREMENT_REFS);
            fixed.put("regulatory_applicability", Map.of("APPLICABLE", 20, "CONDITIONAL", 5,
                "NOT_APPLICABLE", 14, "UNRESOLVED", 0));
            fixed.put("regulatory_runtime_mapping", Map.of("MAPPED", 39, "UNMAPPED", 0));
            fixed.put("rag_metrics", Map.of("hit_at_1", 11.0 / 13.0, "hit_at_3", 1.0, "hit_at_5", 1.0));
            fixed.put("retrieved_context_digest_binding", "PER_EXECUTION_TRACE_AND_BUNDLE");
        }
        if (hasSyntheticEgressEvidence(run.evaluationRunId())) {
            fixed.put("provider_destination_assurance", Map.of(
                "processing_region", "UNRESOLVED",
                "retention", "SESSION_END_DEFAULT_WITH_SECURITY_FRAUD_ABUSE_LOG_EXCEPTION_DURATION_UNSPECIFIED",
                "training_or_reuse", "PERMITTED_FOR_PRODUCT_SERVICE_AND_AI_MODEL_IMPROVEMENT",
                "resolution_path", "FULLY_SYNTHETIC_NON_LINKABLE_MINIMIZED_PAYLOAD",
                "provider_call_authorized", false
            ));
            fixed.put("synthetic_egress_evidence_id", "nvidia-api-trial-synthetic-egress/v1");
            fixed.put("synthetic_egress_evidence_digest", E2_SYNTHETIC_EGRESS_EVIDENCE_DIGEST);
            fixed.put("synthetic_egress_fail_closed", true);
        }
        if (AiEvaluationRunCatalog.EXPERIMENT_02_RUN_ID.equals(run.evaluationRunId())) {
            fixed.put("evaluation_reference_date", retrievalAsOfDate.toString());
            fixed.put("retrieval_window_days", 90);
            fixed.put("synthetic_temporal_provenance_id", "e2-synthetic-temporal-provenance/v1");
            fixed.put("synthetic_temporal_provenance_digest", E2_TEMPORAL_PROVENANCE_DIGEST);
            fixed.put("temporal_consistency_status", "VERIFIED");
        }
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

    public boolean isProviderExecutionAuthorized(String runId) {
        var assurance = required(runId).fixedConditions().path("provider_destination_assurance");
        return assurance.isMissingNode() || assurance.path("provider_call_authorized").asBoolean(false);
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

    private String fixedE1TransformProfileDigest() {
        return canonical.digest(Map.of("profile", "EXPERIMENT_01_FPG_TRANSFORM", "comparison", "DISABLED"));
    }

    private boolean isRegulatoryExperiment02(String runId) {
        return AiEvaluationRunCatalog.EXPERIMENT_02_V3_RUN_ID.equals(runId)
            || AiEvaluationRunCatalog.EXPERIMENT_02_V4_RUN_ID.equals(runId)
            || AiEvaluationRunCatalog.EXPERIMENT_02_RUN_ID.equals(runId);
    }

    private boolean hasSyntheticEgressEvidence(String runId) {
        return AiEvaluationRunCatalog.EXPERIMENT_02_V4_RUN_ID.equals(runId)
            || AiEvaluationRunCatalog.EXPERIMENT_02_RUN_ID.equals(runId);
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
        if (AiEvaluationRunCatalog.EXPERIMENT_02_P1_ROW_REF.equals(datasetRowRef)) return "da-customer-10861";
        if (AiEvaluationRunCatalog.EXPERIMENT_02_P3_ROW_REF.equals(datasetRowRef)) return "da-customer-10202";
        if ("synthetic:customer-100".equals(datasetRowRef)) {
            return "customer-100";
        }
        throw mismatch("AI_CASE_ROW_REF_NOT_SUPPORTED");
    }
    private static AiEvaluationRunMismatchException mismatch(String code) { return new AiEvaluationRunMismatchException(code); }
    private static void require(boolean value, String code) { if (!value) throw mismatch(code); }
}
