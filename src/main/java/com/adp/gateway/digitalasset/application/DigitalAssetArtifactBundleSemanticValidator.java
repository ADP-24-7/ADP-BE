package com.adp.gateway.digitalasset.application;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.adp.gateway.digitalasset.domain.DigitalAssetArtifactFileRole;
import com.adp.gateway.digitalasset.domain.DigitalAssetArtifactControl;
import com.adp.gateway.digitalasset.domain.DigitalAssetDecision;
import com.adp.gateway.digitalasset.domain.DigitalAssetRuntimePipelineStage;
import com.adp.gateway.retrieval.domain.DataClass;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

@Component
public class DigitalAssetArtifactBundleSemanticValidator {
    private static final Set<String> CONTROLS = Arrays.stream(DigitalAssetArtifactControl.values())
        .map(Enum::name)
        .collect(Collectors.toUnmodifiableSet());
    private static final List<String> PIPELINE = Arrays.stream(DigitalAssetRuntimePipelineStage.values())
        .map(Enum::name)
        .toList();
    private static final Set<String> DECISIONS = Arrays.stream(DigitalAssetDecision.values())
        .map(Enum::name)
        .collect(Collectors.toUnmodifiableSet());
    private static final Set<String> RUNTIME_DATA_CLASSES = Arrays.stream(DataClass.values())
        .filter(value -> value != DataClass.UNKNOWN)
        .map(Enum::name)
        .collect(Collectors.toUnmodifiableSet());

    public void validate(JsonNode manifest, Map<DigitalAssetArtifactFileRole, JsonNode> documents) {
        validateBinding(manifest.path("binding"), payload(documents, DigitalAssetArtifactFileRole.BINDING));
        validateCrosswalk(payload(documents, DigitalAssetArtifactFileRole.RUNTIME_DATA_CROSSWALK));
        requireExactSet(
            payload(documents, DigitalAssetArtifactFileRole.POLICY_EVALUATION).path("decision_semantics"),
            DECISIONS, "DIGITAL_ASSET_ARTIFACT_SCHEMA_INVALID"
        );
        JsonNode policy = payload(documents, DigitalAssetArtifactFileRole.POLICY_EVALUATION);
        if (!"DENY".equals(policy.path("external_action_on_unresolved").asText())) {
            throw rejected("DIGITAL_ASSET_ARTIFACT_SCHEMA_INVALID");
        }
        requireExactSet(
            payload(documents, DigitalAssetArtifactFileRole.OUTBOUND_REQUIREMENT_MATRIX).path("controls"),
            CONTROLS, "DIGITAL_ASSET_ARTIFACT_SCHEMA_INVALID"
        );
        validatePipeline(payload(documents, DigitalAssetArtifactFileRole.RUNTIME_PIPELINE));
    }

    private void validateBinding(JsonNode manifestBinding, JsonNode artifactBinding) {
        if (!manifestBinding.path("execution_pack").asText().equals(artifactBinding.path("execution_pack").asText())
            || !manifestBinding.path("workload_id").asText().equals(artifactBinding.path("workload_id").asText())
            || !manifestBinding.path("purpose_code").asText().equals(artifactBinding.path("purpose_code").asText())
            || !manifestBinding.path("destination_profile_id").asText()
                .equals(artifactBinding.path("destination_profile_id").asText())) {
            throw rejected("DIGITAL_ASSET_ARTIFACT_BINDING_INVALID");
        }
    }

    private void validateCrosswalk(JsonNode crosswalk) {
        Set<String> actual = textSet(crosswalk.path("runtime_data_classes"));
        if (actual.isEmpty() || !RUNTIME_DATA_CLASSES.containsAll(actual)) {
            throw rejected("DIGITAL_ASSET_ARTIFACT_BLOCKING_GAP");
        }
    }

    private void validatePipeline(JsonNode pipeline) {
        List<String> actual = new java.util.ArrayList<>();
        pipeline.path("stages").forEach(value -> actual.add(value.asText()));
        if (!PIPELINE.equals(actual)) {
            throw rejected("DIGITAL_ASSET_ARTIFACT_SCHEMA_INVALID");
        }
    }

    private void requireExactSet(JsonNode values, Set<String> expected, String reasonCode) {
        if (!textSet(values).equals(expected) || values.size() != expected.size()) {
            throw rejected(reasonCode);
        }
    }

    private Set<String> textSet(JsonNode values) {
        return java.util.stream.StreamSupport.stream(values.spliterator(), false)
            .map(JsonNode::asText)
            .collect(Collectors.toUnmodifiableSet());
    }

    private JsonNode payload(
        Map<DigitalAssetArtifactFileRole, JsonNode> documents,
        DigitalAssetArtifactFileRole role
    ) {
        JsonNode document = documents.get(role);
        if (document == null) {
            throw rejected("DIGITAL_ASSET_ARTIFACT_REFERENCE_INVALID");
        }
        return document.path("payload");
    }

    private DigitalAssetArtifactIngestionException rejected(String reasonCode) {
        return new DigitalAssetArtifactIngestionException(reasonCode);
    }
}
