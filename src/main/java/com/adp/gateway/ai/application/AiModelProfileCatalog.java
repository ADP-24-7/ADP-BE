package com.adp.gateway.ai.application;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.adp.gateway.ai.domain.AiModelProfile;
import com.adp.gateway.context.application.CanonicalValueHasher;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class AiModelProfileCatalog {
    public static final String PROFILE_VERSION = "2026-09-07";

    private static final String CONNECTION_PROFILE_ID = "nvidia-nim-hosted";
    private final List<AiModelProfile> profiles;
    private final Map<String, AiModelProfile> byProfileId;
    private final Map<String, AiModelProfile> byDestinationId;
    private final ObjectMapper objectMapper;
    private final CanonicalValueHasher hasher;

    public AiModelProfileCatalog(ObjectMapper objectMapper, CanonicalValueHasher hasher) {
        this.objectMapper = objectMapper;
        this.hasher = hasher;
        this.profiles = List.of(
            profile("nvidia-nemotron-3.5-lightning-30b-a3b", "nvidia/nemotron-3.5-lightning-30b-a3b", "1.0-preview"),
            profile("meta-muse-glimmer-30b", "meta/muse-glimmer-30b", "v1.0"),
            profile("google-gemma-4-31b-it", "google/gemma-4-31b-it", "v1.0")
        );
        this.byProfileId = profiles.stream()
            .collect(Collectors.toUnmodifiableMap(AiModelProfile::profileId, Function.identity()));
        this.byDestinationId = profiles.stream()
            .collect(Collectors.toUnmodifiableMap(AiModelProfile::destinationProfileId, Function.identity()));
    }

    public List<AiModelProfile> profiles() {
        return profiles;
    }

    public Optional<AiModelProfile> findByProfileId(String profileId) {
        return profileId == null ? Optional.empty() : Optional.ofNullable(byProfileId.get(profileId));
    }

    public Optional<AiModelProfile> findByDestinationProfileId(String destinationProfileId) {
        return destinationProfileId == null
            ? Optional.empty()
            : Optional.ofNullable(byDestinationId.get(destinationProfileId));
    }

    public String approvalReference(AiModelProfile profile) {
        return "approval_ai_eval_" + profile.profileId();
    }

    public String approvalReference(AiModelProfile profile, String evaluationRunId, String evaluationCaseId) {
        return approvalReference(profile) + "_" + evaluationRunId + "_" + evaluationCaseId;
    }

    public String policySnapshotDigest() {
        return digest(Map.ofEntries(
            Map.entry("policyVersion", "be-runtime-policy/0.0.0"),
            Map.entry("action", "TRANSFORM"),
            Map.entry("workloadId", "customer_summary"),
            Map.entry("purpose", "CUSTOMER_SUPPORT"),
            Map.entry("sourceArtifact", "PROJECT_PROVISIONAL_POLICY_EVALUATION:0.0.0:sha256:local-fixture-policy-evaluation"),
            Map.entry("policyRefs", List.of("PROJECT_PROVISIONAL_POLICY:policy:0.0.0")),
            Map.entry("ruleRefs", List.of("PROJECT_PROVISIONAL_RULE:rule:0.0.0")),
            Map.entry("requirementRefs", List.of("PROJECT_PROVISIONAL_REQUIREMENT:requirement:0.0.0")),
            Map.entry("controls", List.of("RUNTIME_AUTHORIZATION:control:0.0.0", "SUBJECT_SCOPE:control:0.0.0")),
            Map.entry("processingContexts", List.of("AI_USE")),
            Map.entry("dataClasses", List.of("PERSONAL_INFORMATION")),
            Map.entry("binding", "mapped:CUSTOMER_IDENTIFIER:customer_summary:CUSTOMER_SUPPORT:PROJECT_PROVISIONAL_BINDING")
        ));
    }

    public String approvalScopeDigest(AiModelProfile profile, String subjectDigest) {
        return digest(Map.of(
            "institutionId", "institution_local",
            "workloadId", "customer_summary",
            "purpose", "CUSTOMER_SUPPORT",
            "subjectDigest", subjectDigest,
            "destinationProfileId", profile.destinationProfileId(),
            "approvedFields", List.of(
                "request.prompt", "customer.customer_id", "customer.segment", "account.account_id",
                "account.account_type", "account.balance", "transaction.transaction_id",
                "transaction.posted_at", "transaction.merchant_category", "transaction.amount"
            )
        ));
    }

    private AiModelProfile profile(String profileId, String modelId, String modelVersion) {
        String destinationProfileId = "dest_" + profileId.replace('.', '-');
        String modelProfileDigest = modelProfileDigest(
            modelId, modelVersion, 512, 0.0, CONNECTION_PROFILE_ID
        );
        String destinationDigest = digest(Map.of(
            "providerProfileId", profileId,
            "contractVersion", "nvidia-nim-chat-completions/2026-09-07",
            "schemaVersion", "ai-provider-response/v1",
            "tenantId", "tenant_local_ai_evaluation",
            "region", "NVIDIA_HOSTED",
            "retentionPolicy", "PROVIDER_CONTROLLED",
            "trainingUseAllowed", false,
            "bindings", List.of("customer_summary:CUSTOMER_SUPPORT"),
            "fieldContracts", evaluationFieldContracts()
        ));
        return new AiModelProfile(
            profileId,
            PROFILE_VERSION,
            modelId,
            modelVersion,
            CONNECTION_PROFILE_ID,
            modelProfileDigest,
            destinationProfileId,
            PROFILE_VERSION,
            destinationDigest,
            512,
            0.0
        );
    }

    public String modelProfileDigest(
        String modelId,
        String modelVersion,
        int maxTokens,
        double temperature,
        String connectionProfileId
    ) {
        return digest(Map.of(
            "modelId", modelId,
            "modelVersion", modelVersion,
            "maxTokens", maxTokens,
            "temperature", temperature,
            "providerConnectionProfileId", connectionProfileId
        ));
    }

    private List<String> evaluationFieldContracts() {
        return List.of(
            "input.prompt:BUSINESS_METADATA:CONDITIONAL_EXACT:true:true",
            "customer.customer_id:CUSTOMER_IDENTIFIER:PSEUDONYMIZABLE:true:false",
            "customer.segment:BUSINESS_METADATA:CONDITIONAL_EXACT:true:true",
            "account.account_id:ACCOUNT_IDENTIFIER:PSEUDONYMIZABLE:true:false",
            "account.account_type:FINANCIAL_METADATA:CONDITIONAL_EXACT:true:true",
            "account.balance:FINANCIAL_AMOUNT:PSEUDONYMIZABLE:true:false",
            "transaction.transaction_id:TRANSACTION_IDENTIFIER:PSEUDONYMIZABLE:true:false",
            "transaction.posted_at:BUSINESS_METADATA:CONDITIONAL_EXACT:true:true",
            "transaction.merchant_category:BUSINESS_METADATA:CONDITIONAL_EXACT:true:true",
            "transaction.amount:FINANCIAL_AMOUNT:PSEUDONYMIZABLE:true:false"
        );
    }

    private String digest(Object value) {
        try {
            return "sha256:" + hasher.hash(objectMapper.writeValueAsString(canonicalValue(value)));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("AI profile provenance could not be canonicalized", exception);
        }
    }

    private Object canonicalValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> canonical = new TreeMap<>();
            map.forEach((key, item) -> canonical.put(String.valueOf(key), canonicalValue(item)));
            return canonical;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(this::canonicalValue).toList();
        }
        return value;
    }
}
