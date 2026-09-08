package com.adp.gateway.digitalasset.application;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import com.adp.gateway.digitalasset.domain.DigitalAssetArtifactFileRole;
import com.adp.gateway.digitalasset.domain.DigitalAssetCanonicalContract;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import org.springframework.stereotype.Component;

@Component
public class DigitalAssetArtifactBundleValidator {
    public static final String MANIFEST_SCHEMA_VERSION = "adp-digital-asset-artifact-bundle/v1";
    private static final long MANIFEST_MAX_BYTES = 1_048_576;
    private static final long ARTIFACT_MAX_BYTES = 4_194_304;
    private static final Pattern BLOCKING_GAP = Pattern.compile("(^|[^A-Z])(UNMAPPED|TBD|CONTRACT_GAP)([^A-Z]|$)");

    private final ObjectMapper mapper;
    private final DigitalAssetCanonicalJson canonicalJson;
    private final DigitalAssetArtifactSchemaRegistry schemaRegistry;
    private final DigitalAssetArtifactBundleSemanticValidator semanticValidator;
    private final Schema manifestSchema;

    public DigitalAssetArtifactBundleValidator(
        ObjectMapper mapper,
        DigitalAssetCanonicalJson canonicalJson,
        DigitalAssetArtifactSchemaRegistry schemaRegistry,
        DigitalAssetArtifactBundleSemanticValidator semanticValidator
    ) {
        this.mapper = mapper;
        this.canonicalJson = canonicalJson;
        this.schemaRegistry = schemaRegistry;
        this.semanticValidator = semanticValidator;
        this.manifestSchema = loadManifestSchema();
    }

    public ValidatedDigitalAssetArtifactBundle validate(
        String manifestReference,
        String expectedContentDigest,
        DigitalAssetArtifactContentStore store
    ) {
        JsonNode manifest = parse(store.load(manifestReference, MANIFEST_MAX_BYTES),
            "DIGITAL_ASSET_ARTIFACT_MANIFEST_INVALID");
        if (!manifestSchema.validate(manifest).isEmpty()) {
            throw invalid("DIGITAL_ASSET_ARTIFACT_MANIFEST_INVALID");
        }
        verifyManifestDigest(manifest, expectedContentDigest);
        verifyCanonicalContract(manifest.path("canonical_contract"));

        List<ValidatedDigitalAssetArtifactBundle.ArtifactFile> files = new ArrayList<>();
        EnumMap<DigitalAssetArtifactFileRole, JsonNode> documents =
            new EnumMap<>(DigitalAssetArtifactFileRole.class);
        Set<DigitalAssetArtifactFileRole> roles = EnumSet.noneOf(DigitalAssetArtifactFileRole.class);
        Set<String> references = new HashSet<>();
        for (JsonNode file : manifest.path("files")) {
            DigitalAssetArtifactFileRole role = DigitalAssetArtifactFileRole.valueOf(file.path("role").asText());
            String reference = file.path("reference").asText();
            String schemaReference = file.path("schema_reference").asText();
            if (!roles.add(role) || !references.add(reference)) {
                throw invalid("DIGITAL_ASSET_ARTIFACT_REFERENCE_INVALID");
            }
            DigitalAssetArtifactSchemaRegistry.TrustedSchema trustedSchema = schemaRegistry.get(role);
            if (!trustedSchema.reference().equals(schemaReference)) {
                throw invalid("DIGITAL_ASSET_ARTIFACT_REFERENCE_INVALID");
            }
            if (!trustedSchema.digest().equals(file.path("schema_digest").asText())) {
                throw invalid("DIGITAL_ASSET_ARTIFACT_DIGEST_MISMATCH");
            }
            String content = store.load(reference, ARTIFACT_MAX_BYTES);
            JsonNode document = parse(content, "DIGITAL_ASSET_ARTIFACT_SCHEMA_INVALID");
            verifyDigest(document, file.path("digest").asText());
            rejectBlockingGap(document);
            validateDocument(document, trustedSchema.schema());
            documents.put(role, document);
            files.add(new ValidatedDigitalAssetArtifactBundle.ArtifactFile(
                role, document.path("artifact_version").asText(), reference,
                file.path("digest").asText(), schemaReference,
                file.path("schema_digest").asText()
            ));
        }
        if (!roles.equals(EnumSet.allOf(DigitalAssetArtifactFileRole.class))) {
            throw invalid("DIGITAL_ASSET_ARTIFACT_REFERENCE_INVALID");
        }
        semanticValidator.validate(manifest, documents);

        JsonNode binding = manifest.path("binding");
        return new ValidatedDigitalAssetArtifactBundle(
            manifest.path("artifact_id").asText(), manifest.path("artifact_version").asText(),
            stripPrefix(manifest.path("content_digest").asText()), manifest.path("schema_version").asText(),
            manifestReference, binding.path("institution_id").asText(), binding.path("workload_id").asText(),
            binding.path("purpose_code").asText(), binding.path("destination_profile_id").asText(),
            manifest.path("canonical_contract").path("artifact_version").asText(),
            manifest.path("canonical_contract").path("content_digest").asText(), files
        );
    }

    private void verifyManifestDigest(JsonNode manifest, String expectedContentDigest) {
        ObjectNode digestTarget = ((ObjectNode) manifest).deepCopy();
        digestTarget.remove("content_digest");
        String actual = canonicalJson.digest(digestTarget);
        if (!actual.equals(manifest.path("content_digest").asText()) || !actual.equals(expectedContentDigest)) {
            throw invalid("DIGITAL_ASSET_ARTIFACT_DIGEST_MISMATCH");
        }
        if (manifest.path("blocking_gaps").size() != 0) {
            throw invalid("DIGITAL_ASSET_ARTIFACT_BLOCKING_GAP");
        }
    }

    private void verifyCanonicalContract(JsonNode contract) {
        if (!DigitalAssetCanonicalContract.ARTIFACT_ID.equals(contract.path("artifact_id").asText())
            || !DigitalAssetCanonicalContract.ARTIFACT_VERSION.equals(contract.path("artifact_version").asText())
            || !DigitalAssetCanonicalContract.ARTIFACT_CONTENT_DIGEST.equals(contract.path("content_digest").asText())) {
            throw invalid("DIGITAL_ASSET_ARTIFACT_BINDING_INVALID");
        }
    }

    private void verifyDigest(JsonNode document, String expected) {
        if (!canonicalJson.digest(document).equals(expected)) {
            throw invalid("DIGITAL_ASSET_ARTIFACT_DIGEST_MISMATCH");
        }
    }

    private void validateDocument(JsonNode document, Schema schema) {
        try {
            if (!schema.validate(document).isEmpty()) {
                throw invalid("DIGITAL_ASSET_ARTIFACT_SCHEMA_INVALID");
            }
        } catch (DigitalAssetArtifactIngestionException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new DigitalAssetArtifactIngestionException("DIGITAL_ASSET_ARTIFACT_SCHEMA_INVALID", exception);
        }
    }

    private void rejectBlockingGap(JsonNode node) {
        if (node.isTextual() && BLOCKING_GAP.matcher(node.asText().toUpperCase()).find()) {
            throw invalid("DIGITAL_ASSET_ARTIFACT_BLOCKING_GAP");
        }
        node.forEach(this::rejectBlockingGap);
    }

    private JsonNode parse(String content, String reasonCode) {
        try {
            return mapper.readTree(content);
        } catch (IOException exception) {
            throw new DigitalAssetArtifactIngestionException(reasonCode, exception);
        }
    }

    private Schema loadManifestSchema() {
        try (InputStream input = getClass().getResourceAsStream(
            "/contracts/digital-asset-artifact-bundle-v1.schema.json"
        )) {
            if (input == null) {
                throw new IllegalStateException("Digital Asset artifact manifest schema is missing");
            }
            return SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
                .getSchema(mapper.readTree(input));
        } catch (IOException exception) {
            throw new IllegalStateException("Digital Asset artifact manifest schema cannot be loaded", exception);
        }
    }

    private String stripPrefix(String digest) {
        return digest.substring("sha256:".length());
    }

    private DigitalAssetArtifactIngestionException invalid(String reasonCode) {
        return new DigitalAssetArtifactIngestionException(reasonCode);
    }
}
