package com.adp.gateway.digitalasset.application;

import java.io.IOException;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.Map;

import com.adp.gateway.digitalasset.domain.DigitalAssetArtifactFileRole;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import org.springframework.stereotype.Component;

@Component
public class DigitalAssetArtifactSchemaRegistry {
    private static final String ROOT = "contracts/digital-asset-artifacts/";

    private final Map<DigitalAssetArtifactFileRole, TrustedSchema> schemas;

    public DigitalAssetArtifactSchemaRegistry(ObjectMapper mapper, DigitalAssetCanonicalJson canonicalJson) {
        EnumMap<DigitalAssetArtifactFileRole, TrustedSchema> loaded =
            new EnumMap<>(DigitalAssetArtifactFileRole.class);
        register(loaded, mapper, canonicalJson, DigitalAssetArtifactFileRole.OUTBOUND_REQUIREMENT_MATRIX,
            "outbound-requirement-matrix-v1.schema.json");
        register(loaded, mapper, canonicalJson, DigitalAssetArtifactFileRole.POLICY_EVALUATION,
            "policy-evaluation-v1.schema.json");
        register(loaded, mapper, canonicalJson, DigitalAssetArtifactFileRole.BINDING,
            "binding-v1.schema.json");
        register(loaded, mapper, canonicalJson, DigitalAssetArtifactFileRole.RUNTIME_DATA_CROSSWALK,
            "runtime-data-crosswalk-v1.schema.json");
        register(loaded, mapper, canonicalJson, DigitalAssetArtifactFileRole.RUNTIME_PIPELINE,
            "runtime-pipeline-v1.schema.json");
        schemas = Map.copyOf(loaded);
    }

    public TrustedSchema get(DigitalAssetArtifactFileRole role) {
        TrustedSchema schema = schemas.get(role);
        if (schema == null) {
            throw new DigitalAssetArtifactIngestionException("DIGITAL_ASSET_ARTIFACT_SCHEMA_INVALID");
        }
        return schema;
    }

    private void register(
        EnumMap<DigitalAssetArtifactFileRole, TrustedSchema> target,
        ObjectMapper mapper,
        DigitalAssetCanonicalJson canonicalJson,
        DigitalAssetArtifactFileRole role,
        String fileName
    ) {
        String reference = ROOT + fileName;
        try (InputStream input = getClass().getResourceAsStream("/" + reference)) {
            if (input == null) {
                throw new IllegalStateException("Trusted Digital Asset artifact schema is missing: " + role);
            }
            JsonNode document = mapper.readTree(input);
            Schema schema = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
                .getSchema(document);
            target.put(role, new TrustedSchema(reference, canonicalJson.digest(document), schema));
        } catch (IOException exception) {
            throw new IllegalStateException("Trusted Digital Asset artifact schema cannot be loaded: " + role, exception);
        }
    }

    public record TrustedSchema(String reference, String digest, Schema schema) {
    }
}
