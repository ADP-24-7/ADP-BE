package com.adp.gateway.digitalasset.contract;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import com.adp.gateway.common.error.ReasonCode;
import com.adp.gateway.context.application.ExecutionPackRequestScope;
import com.adp.gateway.decision.domain.FinalAction;
import com.adp.gateway.digitalasset.application.DigitalAssetCanonicalJson;
import com.adp.gateway.digitalasset.domain.DigitalAssetCanonicalContract;
import com.adp.gateway.digitalasset.domain.DigitalAssetDecision;
import com.adp.gateway.digitalasset.domain.DigitalAssetExternalStatus;
import com.adp.gateway.digitalasset.domain.DigitalAssetFinalityStatus;
import com.adp.gateway.digitalasset.domain.DigitalAssetKind;
import com.adp.gateway.digitalasset.domain.DigitalAssetMismatchField;
import com.adp.gateway.digitalasset.domain.DigitalAssetOperation;
import com.adp.gateway.digitalasset.domain.DigitalAssetProviderStatus;
import com.adp.gateway.digitalasset.domain.DigitalAssetReceiptStatus;
import com.adp.gateway.digitalasset.domain.DigitalAssetReconciliationResult;
import com.adp.gateway.digitalasset.domain.DigitalAssetRuntimeInput;
import com.adp.gateway.digitalasset.domain.ExternalExecutionResult;
import com.adp.gateway.retrieval.domain.DataClass;
import com.adp.gateway.transform.domain.TransformStrategy;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import org.junit.jupiter.api.Test;

class DigitalAssetCanonicalContractTests {
    private static final Path SCHEMA_PATH =
        Path.of("docs/contracts/digital-asset-runtime-contract-v1.schema.json");
    private static final Path MANIFEST_PATH =
        Path.of("docs/contracts/digital-asset-runtime-contract-v1.json");
    private static final Path REQUEST_SAMPLE_PATH =
        Path.of("docs/contracts/samples/digital-asset-runtime-request-v1.json");
    private static final Path RESULT_SAMPLE_PATH =
        Path.of("docs/contracts/samples/digital-asset-external-result-v1.json");

    private final ObjectMapper mapper = new ObjectMapper();
    private final DigitalAssetCanonicalJson canonicalJson = new DigitalAssetCanonicalJson();

    @Test
    void validatesManifestAndPublishedWireSamples() throws IOException {
        JsonNode schemaNode = read(SCHEMA_PATH);
        JsonNode requestSample = read(REQUEST_SAMPLE_PATH);
        JsonNode resultSample = read(RESULT_SAMPLE_PATH);

        assertValid(SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
            .getSchema(schemaNode), read(MANIFEST_PATH));
        assertValid(schemaAt(schemaNode, "runtime_request"), requestSample);
        assertValid(schemaAt(schemaNode, "external_execution_result"), resultSample);

        Map<String, Object> input = mapper.convertValue(
            requestSample.path("input"), new TypeReference<Map<String, Object>>() { }
        );
        DigitalAssetRuntimeInput.from(input, new ExecutionPackRequestScope(
            "institution_local",
            DigitalAssetCanonicalContract.BASELINE_WORKLOAD_ID,
            DigitalAssetCanonicalContract.BASELINE_PURPOSE_CODE,
            "subject-digest-sample",
            DigitalAssetCanonicalContract.BASELINE_DESTINATION_PROFILE_ID,
            "sample-digital-asset-request-v1",
            OffsetDateTime.parse("2026-09-08T00:00:00Z")
        ));
        ExternalExecutionResult.from(
            mapper.convertValue(resultSample, new TypeReference<Map<String, Object>>() { }),
            "sha256:external-result-sample"
        );
    }

    @Test
    void freezesSchemaEnumsToJavaSourceOfTruth() throws IOException {
        JsonNode schema = read(SCHEMA_PATH);
        assertSchemaEnum(schema, "runtime_data_class_set", enumNames(DataClass.class));
        assertSchemaEnum(
            schema,
            "canonical_field_set",
            DigitalAssetMismatchField.canonicalExecutionFields().stream()
                .map(DigitalAssetMismatchField::externalName)
                .collect(Collectors.toUnmodifiableSet())
        );
        assertSchemaEnum(schema, "decision_set", enumNames(DigitalAssetDecision.class));
        assertSchemaEnum(schema, "transform_strategy_set", enumNames(TransformStrategy.class));
        assertSchemaEnum(schema, "asset_kind_set", enumNames(DigitalAssetKind.class));
        assertSchemaEnum(schema, "operation_set", enumNames(DigitalAssetOperation.class));
        assertSchemaEnum(schema, "external_status_set", enumNames(DigitalAssetExternalStatus.class));
        assertSchemaEnum(schema, "provider_status_set", enumNames(DigitalAssetProviderStatus.class));
        assertSchemaEnum(schema, "receipt_status_set", enumNames(DigitalAssetReceiptStatus.class));
        assertSchemaEnum(schema, "finality_status_set", enumNames(DigitalAssetFinalityStatus.class));
        assertSchemaEnum(schema, "reconciliation_result_set", enumNames(DigitalAssetReconciliationResult.class));
        assertSchemaEnum(
            schema,
            "reason_code_set",
            DigitalAssetCanonicalContract.ACTIVE_REASON_CODES.stream()
                .map(ReasonCode::name)
                .collect(Collectors.toUnmodifiableSet())
        );
    }

    @Test
    void freezesManifestIdentityAndEnumInventory() throws IOException {
        JsonNode manifest = read(MANIFEST_PATH);
        assertThat(manifest.path("schema_version").asText())
            .isEqualTo(DigitalAssetCanonicalContract.SCHEMA_VERSION);
        assertThat(manifest.path("artifact_id").asText())
            .isEqualTo(DigitalAssetCanonicalContract.ARTIFACT_ID);
        assertThat(manifest.path("artifact_version").asText())
            .isEqualTo(DigitalAssetCanonicalContract.ARTIFACT_VERSION);
        assertThat(manifest.path("canonicalization_version").asText())
            .isEqualTo(DigitalAssetCanonicalContract.CANONICALIZATION_VERSION);

        JsonNode identifiers = manifest.path("contract").path("identifiers");
        assertThat(identifiers.path("execution_pack").asText())
            .isEqualTo(DigitalAssetCanonicalContract.EXECUTION_PACK);
        assertThat(identifiers.path("workload_id").asText())
            .isEqualTo(DigitalAssetCanonicalContract.BASELINE_WORKLOAD_ID);
        assertThat(identifiers.path("purpose_code").asText())
            .isEqualTo(DigitalAssetCanonicalContract.BASELINE_PURPOSE_CODE);
        assertThat(identifiers.path("destination_profile_id").asText())
            .isEqualTo(DigitalAssetCanonicalContract.BASELINE_DESTINATION_PROFILE_ID);
        assertThat(identifiers.path("destination_profile_version").asText())
            .isEqualTo(DigitalAssetCanonicalContract.BASELINE_DESTINATION_PROFILE_VERSION);

        JsonNode enumSets = manifest.path("contract").path("enum_sets");
        assertThat(textSet(enumSets.path("canonical_fields"))).isEqualTo(
            DigitalAssetMismatchField.canonicalExecutionFields().stream()
                .map(DigitalAssetMismatchField::externalName)
                .collect(Collectors.toUnmodifiableSet())
        );
        assertThat(textSet(enumSets.path("decisions"))).isEqualTo(enumNames(DigitalAssetDecision.class));
        assertThat(textSet(enumSets.path("transform_strategies"))).isEqualTo(enumNames(TransformStrategy.class));
        assertThat(textSet(enumSets.path("reason_codes"))).isEqualTo(
            DigitalAssetCanonicalContract.ACTIVE_REASON_CODES.stream()
                .map(ReasonCode::name)
                .collect(Collectors.toUnmodifiableSet())
        );
    }

    @Test
    void verifiesManifestAndEveryPublishedFileDigest() throws IOException {
        JsonNode manifest = read(MANIFEST_PATH);
        ObjectNode digestContent = ((ObjectNode) manifest).deepCopy();
        digestContent.remove("content_digest");
        assertThat(canonicalJson.digest(digestContent))
            .isEqualTo(manifest.path("content_digest").asText());

        for (JsonNode file : manifest.path("contract").path("files")) {
            Path path = Path.of(file.path("path").asText()).normalize();
            assertThat(path.startsWith(Path.of("docs/contracts"))).isTrue();
            assertThat(Files.isRegularFile(path)).isTrue();
            assertThat(canonicalJson.digest(read(path))).isEqualTo(file.path("digest").asText());
        }
    }

    @Test
    void canonicalSerializationSortsObjectKeysPreservesNullAndKeepsArrayOrder() {
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("z", 2);
        first.put("items", java.util.List.of("A", "B"));
        first.put("nullable", null);
        first.put("a", 1);

        Map<String, Object> second = new LinkedHashMap<>();
        second.put("a", 1);
        second.put("nullable", null);
        second.put("items", java.util.List.of("A", "B"));
        second.put("z", 2);

        assertThat(canonicalJson.serialize(first))
            .isEqualTo("{\"a\":1,\"items\":[\"A\",\"B\"],\"nullable\":null,\"z\":2}")
            .isEqualTo(canonicalJson.serialize(second));
        assertThat(canonicalJson.digest(first)).isEqualTo(canonicalJson.digest(second));

        second.put("items", java.util.List.of("B", "A"));
        assertThat(canonicalJson.digest(second)).isNotEqualTo(canonicalJson.digest(first));
    }

    @Test
    void schemaEnforcesConditionalAssetFieldsAndRejectsUnknownFields() throws IOException {
        JsonNode schemaNode = read(SCHEMA_PATH);
        Schema requestSchema = schemaAt(schemaNode, "runtime_request");
        ObjectNode fungible = (ObjectNode) read(REQUEST_SAMPLE_PATH);
        assertValid(requestSchema, fungible);

        ObjectNode asset = requestedAsset(fungible);
        asset.remove("assetContractAddress");
        assertInvalid(requestSchema, fungible);

        ObjectNode nativeRequest = (ObjectNode) read(REQUEST_SAMPLE_PATH);
        ObjectNode nativeAsset = requestedAsset(nativeRequest);
        nativeAsset.put("assetKind", "NATIVE");
        nativeAsset.remove("assetContractAddress");
        assertValid(requestSchema, nativeRequest);

        ObjectNode nftRequest = (ObjectNode) read(REQUEST_SAMPLE_PATH);
        ObjectNode nftAsset = requestedAsset(nftRequest);
        nftAsset.put("assetKind", "NON_FUNGIBLE_TOKEN");
        nftAsset.put("tokenId", "42");
        assertValid(requestSchema, nftRequest);
        nftAsset.remove("tokenId");
        assertInvalid(requestSchema, nftRequest);

        ObjectNode unknownFieldRequest = (ObjectNode) read(REQUEST_SAMPLE_PATH);
        requestedAsset(unknownFieldRequest).put("network", "ethereum");
        assertInvalid(requestSchema, unknownFieldRequest);
    }

    @Test
    void mapsCommonFinalActionToTransformIndependentDigitalAssetDecision() {
        assertThat(DigitalAssetDecision.from(FinalAction.ALLOW)).isEqualTo(DigitalAssetDecision.PASS);
        assertThat(DigitalAssetDecision.from(FinalAction.TRANSFORM)).isEqualTo(DigitalAssetDecision.PASS);
        assertThat(DigitalAssetDecision.from(FinalAction.REVIEW)).isEqualTo(DigitalAssetDecision.REVIEW);
        assertThat(DigitalAssetDecision.from(FinalAction.BLOCK)).isEqualTo(DigitalAssetDecision.BLOCK);
    }

    private JsonNode read(Path path) throws IOException {
        return mapper.readTree(Files.readString(path));
    }

    private Schema schemaAt(JsonNode root, String definition) {
        ObjectNode wrapper = mapper.createObjectNode();
        wrapper.put("$schema", "https://json-schema.org/draft/2020-12/schema");
        wrapper.put("$ref", "#/$defs/" + definition);
        wrapper.set("$defs", root.path("$defs").deepCopy());
        return SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12).getSchema(wrapper);
    }

    private void assertSchemaEnum(JsonNode schema, String definition, Set<String> expected) {
        assertThat(textSet(schema.path("$defs").path(definition).path("items").path("enum")))
            .isEqualTo(expected);
    }

    private Set<String> textSet(JsonNode array) {
        return StreamSupport.stream(array.spliterator(), false)
            .map(JsonNode::asText)
            .collect(Collectors.toUnmodifiableSet());
    }

    private <T extends Enum<T>> Set<String> enumNames(Class<T> type) {
        return Arrays.stream(type.getEnumConstants())
            .map(Enum::name)
            .collect(Collectors.toUnmodifiableSet());
    }

    private ObjectNode requestedAsset(ObjectNode request) {
        return (ObjectNode) request.path("input").path("outboundRequest").path("requestedAsset");
    }

    private void assertValid(Schema schema, JsonNode value) {
        assertThat(schema.validate(value)).isEmpty();
    }

    private void assertInvalid(Schema schema, JsonNode value) {
        assertThat(schema.validate(value)).isNotEmpty();
    }
}
