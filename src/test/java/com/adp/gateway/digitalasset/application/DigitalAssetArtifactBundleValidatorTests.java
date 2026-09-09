package com.adp.gateway.digitalasset.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.adp.gateway.digitalasset.domain.DigitalAssetArtifactControl;
import com.adp.gateway.digitalasset.domain.DigitalAssetDecision;
import com.adp.gateway.digitalasset.domain.DigitalAssetRuntimePipelineStage;
import com.adp.gateway.digitalasset.infrastructure.LocalDigitalAssetArtifactContentStore;
import com.adp.gateway.retrieval.domain.DataClass;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DigitalAssetArtifactBundleValidatorTests {
    private static final String ROOT = "docs/contracts/artifacts/p0-5-sample/";
    private static final String MANIFEST = ROOT + "manifest.json";
    private final ObjectMapper mapper = new ObjectMapper();
    private final DigitalAssetCanonicalJson canonicalJson = new DigitalAssetCanonicalJson();
    private DigitalAssetArtifactBundleValidator validator;
    private MapStore store;

    @BeforeEach
    void setUp() throws IOException {
        validator = new DigitalAssetArtifactBundleValidator(
            mapper,
            canonicalJson,
            new DigitalAssetArtifactSchemaRegistry(mapper, canonicalJson),
            new DigitalAssetArtifactBundleSemanticValidator()
        );
        store = new MapStore();
        try (var files = Files.list(Path.of(ROOT))) {
            for (Path path : files.toList()) {
                store.values.put(ROOT + path.getFileName(), Files.readString(path));
            }
        }
    }

    @Test
    void validatesCompleteBundleAndCanonicalBinding() throws IOException {
        String expected = mapper.readTree(store.values.get(MANIFEST)).path("content_digest").asText();

        ValidatedDigitalAssetArtifactBundle result = validator.validate(MANIFEST, expected, store);

        assertThat(result.artifactId()).isEqualTo("DA-DIGITAL-ASSET-RUNTIME-CANDIDATE-001");
        assertThat(result.files()).hasSize(5);
        assertThat(result.workloadId()).isEqualTo("tokenized_asset_purchase");
    }

    @Test
    void rejectsDigestTamperAndMissingReference() throws IOException {
        String expected = mapper.readTree(store.values.get(MANIFEST)).path("content_digest").asText();
        ObjectNode binding = (ObjectNode) mapper.readTree(store.values.get(ROOT + "binding.json"));
        ((ObjectNode) binding.path("payload")).put("destination_profile_id", "tampered-destination");
        store.values.put(ROOT + "binding.json", mapper.writeValueAsString(binding));
        assertThatThrownBy(() -> validator.validate(MANIFEST, expected, store))
            .isInstanceOf(DigitalAssetArtifactIngestionException.class)
            .extracting(exception -> ((DigitalAssetArtifactIngestionException) exception).reasonCode())
            .isEqualTo("DIGITAL_ASSET_ARTIFACT_DIGEST_MISMATCH");

        setUp();
        store.values.remove(ROOT + "binding.json");
        assertThatThrownBy(() -> validator.validate(MANIFEST, expected, store))
            .isInstanceOf(DigitalAssetArtifactIngestionException.class)
            .extracting(exception -> ((DigitalAssetArtifactIngestionException) exception).reasonCode())
            .isEqualTo("DIGITAL_ASSET_ARTIFACT_REFERENCE_INVALID");
    }

    @Test
    void rejectsSchemaFailureAndBlockingContractGap() throws IOException {
        mutateDocument("POLICY_EVALUATION", document -> document.put("status", "DRAFT"));
        String invalidSchemaDigest = refreshManifestDigest();
        assertThatThrownBy(() -> validator.validate(MANIFEST, invalidSchemaDigest, store))
            .isInstanceOf(DigitalAssetArtifactIngestionException.class)
            .extracting(exception -> ((DigitalAssetArtifactIngestionException) exception).reasonCode())
            .isEqualTo("DIGITAL_ASSET_ARTIFACT_SCHEMA_INVALID");

        setUp();
        mutateDocument("POLICY_EVALUATION", document ->
            ((ObjectNode) document.path("payload")).put("mapping_status", "CONTRACT_GAP")
        );
        String blockingGapDigest = refreshManifestDigest();
        assertThatThrownBy(() -> validator.validate(MANIFEST, blockingGapDigest, store))
            .isInstanceOf(DigitalAssetArtifactIngestionException.class)
            .extracting(exception -> ((DigitalAssetArtifactIngestionException) exception).reasonCode())
            .isEqualTo("DIGITAL_ASSET_ARTIFACT_BLOCKING_GAP");
    }

    @Test
    void rejectsProducerSelectedSchemaAndCrossArtifactBindingMismatch() throws IOException {
        ObjectNode manifest = (ObjectNode) mapper.readTree(store.values.get(MANIFEST));
        ((ObjectNode) findFile(manifest, "BINDING"))
            .put("schema_reference", "producer/permissive.schema.json");
        store.values.put(MANIFEST, mapper.writeValueAsString(manifest));
        String untrustedSchemaDigest = refreshManifestDigest();

        assertThatThrownBy(() -> validator.validate(MANIFEST, untrustedSchemaDigest, store))
            .isInstanceOf(DigitalAssetArtifactIngestionException.class)
            .extracting(exception -> ((DigitalAssetArtifactIngestionException) exception).reasonCode())
            .isEqualTo("DIGITAL_ASSET_ARTIFACT_REFERENCE_INVALID");

        setUp();
        mutateDocument("BINDING", document ->
            ((ObjectNode) document.path("payload")).put("destination_profile_id", "dest-other")
        );
        String bindingMismatchDigest = refreshManifestDigest();
        assertThatThrownBy(() -> validator.validate(MANIFEST, bindingMismatchDigest, store))
            .isInstanceOf(DigitalAssetArtifactIngestionException.class)
            .extracting(exception -> ((DigitalAssetArtifactIngestionException) exception).reasonCode())
            .isEqualTo("DIGITAL_ASSET_ARTIFACT_BINDING_INVALID");
    }

    @Test
    void rejectsUnknownRuntimeDataClassAndPipelineOrderChange() throws IOException {
        mutateDocument("RUNTIME_DATA_CROSSWALK", document ->
            ((com.fasterxml.jackson.databind.node.ArrayNode) document.path("payload")
                .path("runtime_data_classes")).add("UNKNOWN")
        );
        String unknownDigest = refreshManifestDigest();
        assertThatThrownBy(() -> validator.validate(MANIFEST, unknownDigest, store))
            .isInstanceOf(DigitalAssetArtifactIngestionException.class)
            .extracting(exception -> ((DigitalAssetArtifactIngestionException) exception).reasonCode())
            .isEqualTo("DIGITAL_ASSET_ARTIFACT_BLOCKING_GAP");

        setUp();
        mutateDocument("RUNTIME_PIPELINE", document -> {
            var stages = (com.fasterxml.jackson.databind.node.ArrayNode) document.path("payload").path("stages");
            JsonNode first = stages.get(0);
            stages.set(0, stages.get(1));
            stages.set(1, first);
        });
        String pipelineDigest = refreshManifestDigest();
        assertThatThrownBy(() -> validator.validate(MANIFEST, pipelineDigest, store))
            .isInstanceOf(DigitalAssetArtifactIngestionException.class)
            .extracting(exception -> ((DigitalAssetArtifactIngestionException) exception).reasonCode())
            .isEqualTo("DIGITAL_ASSET_ARTIFACT_SCHEMA_INVALID");
    }

    @Test
    void trustedSchemasRemainBoundToCanonicalJavaEnums() throws IOException {
        JsonNode policySchema = mapper.readTree(Files.readString(Path.of(
            "src/main/resources/contracts/digital-asset-artifacts/policy-evaluation-v1.schema.json"
        )));
        JsonNode crosswalkSchema = mapper.readTree(Files.readString(Path.of(
            "src/main/resources/contracts/digital-asset-artifacts/runtime-data-crosswalk-v1.schema.json"
        )));
        JsonNode controlSchema = mapper.readTree(Files.readString(Path.of(
            "src/main/resources/contracts/digital-asset-artifacts/outbound-requirement-matrix-v1.schema.json"
        )));
        JsonNode pipelineSchema = mapper.readTree(Files.readString(Path.of(
            "src/main/resources/contracts/digital-asset-artifacts/runtime-pipeline-v1.schema.json"
        )));

        assertThat(textSet(policySchema.path("properties").path("payload").path("properties")
            .path("decision_semantics").path("items").path("enum")))
            .isEqualTo(Arrays.stream(DigitalAssetDecision.values())
                .map(Enum::name).collect(Collectors.toSet()));
        assertThat(textSet(crosswalkSchema.path("properties").path("payload").path("properties")
            .path("runtime_data_classes").path("items").path("enum")))
            .isEqualTo(Arrays.stream(DataClass.values()).map(Enum::name).collect(Collectors.toSet()));
        assertThat(textSet(controlSchema.path("properties").path("payload").path("properties")
            .path("controls").path("items").path("enum")))
            .isEqualTo(Arrays.stream(DigitalAssetArtifactControl.values())
                .map(Enum::name).collect(Collectors.toSet()));
        assertThat(java.util.stream.StreamSupport.stream(
                pipelineSchema.path("properties").path("payload").path("properties")
                    .path("stages").path("prefixItems").spliterator(), false
            ).map(item -> item.path("const").asText()).toList())
            .containsExactly(Arrays.stream(DigitalAssetRuntimePipelineStage.values())
                .map(Enum::name).toArray(String[]::new));
    }

    @Test
    void localStoreRejectsTraversalBeforeReading() {
        LocalDigitalAssetArtifactContentStore local = new LocalDigitalAssetArtifactContentStore(".");
        assertThatThrownBy(() -> local.load("../ADP-DA/secret.json", 1024))
            .isInstanceOf(DigitalAssetArtifactIngestionException.class)
            .extracting(exception -> ((DigitalAssetArtifactIngestionException) exception).reasonCode())
            .isEqualTo("DIGITAL_ASSET_ARTIFACT_REFERENCE_INVALID");
    }

    private void mutateDocument(String role, java.util.function.Consumer<ObjectNode> mutation) throws IOException {
        ObjectNode manifest = (ObjectNode) mapper.readTree(store.values.get(MANIFEST));
        JsonNode file = findFile(manifest, role);
        String reference = file.path("reference").asText();
        ObjectNode document = (ObjectNode) mapper.readTree(store.values.get(reference));
        mutation.accept(document);
        store.values.put(reference, mapper.writeValueAsString(document));
        ((ObjectNode) file).put("digest", canonicalJson.digest(document));
        store.values.put(MANIFEST, mapper.writeValueAsString(manifest));
    }

    private String refreshManifestDigest() throws IOException {
        ObjectNode manifest = (ObjectNode) mapper.readTree(store.values.get(MANIFEST));
        ObjectNode target = manifest.deepCopy();
        target.remove("content_digest");
        String digest = canonicalJson.digest(target);
        manifest.put("content_digest", digest);
        store.values.put(MANIFEST, mapper.writeValueAsString(manifest));
        return digest;
    }

    private JsonNode findFile(JsonNode manifest, String role) {
        for (JsonNode file : manifest.path("files")) {
            if (role.equals(file.path("role").asText())) {
                return file;
            }
        }
        throw new IllegalArgumentException("role not found");
    }

    private Set<String> textSet(JsonNode values) {
        return java.util.stream.StreamSupport.stream(values.spliterator(), false)
            .map(JsonNode::asText)
            .collect(Collectors.toSet());
    }

    private static final class MapStore implements DigitalAssetArtifactContentStore {
        private final Map<String, String> values = new HashMap<>();

        @Override
        public String load(String reference, long maxBytes) {
            String value = values.get(reference);
            if (value == null) {
                throw new DigitalAssetArtifactIngestionException("DIGITAL_ASSET_ARTIFACT_REFERENCE_INVALID");
            }
            return value;
        }
    }
}
