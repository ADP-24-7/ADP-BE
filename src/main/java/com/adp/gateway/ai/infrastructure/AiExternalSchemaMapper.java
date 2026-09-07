package com.adp.gateway.ai.infrastructure;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import com.adp.gateway.ai.application.AiModelProfileCatalog;
import com.adp.gateway.ai.application.AiModelExecutionEvidencePort;
import com.adp.gateway.ai.application.AiEvaluationRunCatalog;
import com.adp.gateway.ai.application.AiEvaluationRunMismatchException;
import com.adp.gateway.ai.domain.AiEvaluationReference;
import com.adp.gateway.context.application.CanonicalValueHasher;
import com.adp.gateway.egress.application.ExternalSchemaMapper;
import com.adp.gateway.egress.domain.DestinationProfile;
import com.adp.gateway.egress.domain.ExecutionPackType;
import com.adp.gateway.egress.domain.OutboundCandidatePayload;
import com.adp.gateway.egress.domain.ProviderRequestPayload;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class AiExternalSchemaMapper implements ExternalSchemaMapper {

    private final ObjectMapper objectMapper;
    private final CanonicalValueHasher hasher;
    private final AiModelProfileCatalog modelProfiles;
    private final AiModelExecutionEvidencePort evidencePort;
    private final AiEvaluationRunCatalog evaluationRuns;

    public AiExternalSchemaMapper(
        ObjectMapper objectMapper,
        CanonicalValueHasher hasher,
        AiModelProfileCatalog modelProfiles,
        AiModelExecutionEvidencePort evidencePort,
        AiEvaluationRunCatalog evaluationRuns
    ) {
        this.objectMapper = objectMapper;
        this.hasher = hasher;
        this.modelProfiles = modelProfiles;
        this.evidencePort = evidencePort;
        this.evaluationRuns = evaluationRuns;
    }

    @Override
    public ExecutionPackType supportedPack() {
        return ExecutionPackType.AI;
    }

    @Override
    public ProviderRequestPayload map(
        String executionId,
        AiEvaluationReference evaluationReference,
        DestinationProfile destinationProfile,
        OutboundCandidatePayload outboundPayload
    ) {
        if (destinationProfile.packType() != ExecutionPackType.AI) {
            throw new IllegalArgumentException("AI schema mapper cannot map a non-AI execution pack");
        }
        Map<String, Object> fields = new TreeMap<>();
        outboundPayload.fields().forEach(field -> fields.put(field.path(), field.value()));
        String providerCorrelationKey = "preq_" + UUID.randomUUID();
        var modelProfile = modelProfiles.findByProfileId(destinationProfile.providerProfileId());
        var evaluationRun = evaluationReference == null ? null : evaluationRuns
            .find(evaluationReference.evaluationRunId())
            .orElseThrow(() -> new AiEvaluationRunMismatchException("AI_EVALUATION_RUN_NOT_FOUND"));
        if (evaluationRun != null && (!evaluationRun.evalCaseIds().contains(evaluationReference.evalCaseId())
            || modelProfile.isEmpty()
            || !evaluationRun.modelProfileIds().contains(modelProfile.get().profileId())
            || !evaluationRun.policySnapshotDigest().equals(evaluationReference.policySnapshotDigest())
            || !modelProfile.get().destinationProfileDigest().equals(destinationProfile.profileDigest()))) {
            throw new AiEvaluationRunMismatchException("AI_EVALUATION_SCOPE_MISMATCH");
        }
        modelProfile.ifPresent(profile -> evidencePort.record(
            executionId,
            profile,
            evaluationRun,
            evaluationReference == null ? null : evaluationReference.evalCaseId(),
            destinationProfile.profileDigest()
        ));
        Map<String, Object> payload = modelProfile
            .map(profile -> nvidiaPayload(profile.modelId(), profile.maxTokens(), profile.temperature(), fields))
            .orElseGet(() -> legacyPayload(destinationProfile, providerCorrelationKey, fields));
        try {
            String canonicalJson = objectMapper.writeValueAsString(payload);
            return new ProviderRequestPayload(
                providerCorrelationKey,
                outboundPayload.outboundPayloadId(),
                destinationProfile.providerProfileId(),
                destinationProfile.schemaVersion(),
                hasher.hash(canonicalJson),
                fields.size(),
                payload
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("AI provider request could not be canonicalized", exception);
        }
    }

    private Map<String, Object> nvidiaPayload(
        String modelId,
        int maxTokens,
        double temperature,
        Map<String, Object> fields
    ) {
        Map<String, Object> payload = new TreeMap<>();
        payload.put("max_tokens", maxTokens);
        payload.put("messages", List.of(Map.of(
            "role", "user",
            "content", canonicalJson(fields)
        )));
        payload.put("model", modelId);
        payload.put("stream", false);
        payload.put("temperature", temperature);
        return payload;
    }

    private String canonicalJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("AI provider payload could not be canonicalized", exception);
        }
    }

    private Map<String, Object> legacyPayload(
        DestinationProfile destinationProfile,
        String providerCorrelationKey,
        Map<String, Object> fields
    ) {
        Map<String, Object> payload = new TreeMap<>();
        payload.put("context", fields);
        payload.put("externalRequestId", providerCorrelationKey);
        payload.put("schemaVersion", destinationProfile.schemaVersion());
        payload.put("tenant", destinationProfile.tenantId());
        return payload;
    }
}
