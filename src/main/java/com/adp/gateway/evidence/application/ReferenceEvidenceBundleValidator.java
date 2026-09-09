package com.adp.gateway.evidence.application;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.adp.gateway.evidence.domain.ReferenceEvidenceStatus;
import com.adp.gateway.evidence.domain.ReferenceEvidenceType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import org.springframework.stereotype.Component;

@Component
public class ReferenceEvidenceBundleValidator {
    public static final String SCHEMA_VERSION = "adp-reference-evidence-bundle/v1";

    private final ObjectMapper mapper;
    private final ReferenceEvidenceCanonicalJson canonicalJson;
    private final Schema schema;

    public ReferenceEvidenceBundleValidator(
        ObjectMapper mapper,
        ReferenceEvidenceCanonicalJson canonicalJson
    ) {
        this.mapper = mapper;
        this.canonicalJson = canonicalJson;
        this.schema = loadSchema();
    }

    public ValidatedReferenceEvidenceBundle validate(JsonNode bundle) {
        try {
            if (bundle == null || !bundle.isObject() || !schema.validate(bundle).isEmpty()) {
                throw invalid("REFERENCE_EVIDENCE_SCHEMA_INVALID");
            }
            verifyDigest(bundle, "content_digest");
            if (bundle.path("evidence_count").asInt() != bundle.path("evidence").size()) {
                throw invalid("REFERENCE_EVIDENCE_SCHEMA_INVALID");
            }

            Set<String> identities = new HashSet<>();
            List<ValidatedReferenceEvidenceBundle.Item> items = new ArrayList<>();
            for (JsonNode evidence : bundle.path("evidence")) {
                verifyDigest(evidence, "content_digest");
                String evidenceId = evidence.path("evidence_id").asText();
                String evidenceVersion = evidence.path("evidence_version").asText();
                if (!identities.add(evidenceId + "\u001f" + evidenceVersion)) {
                    throw invalid("REFERENCE_EVIDENCE_SCHEMA_INVALID");
                }
                ReferenceEvidenceStatus status = ReferenceEvidenceStatus.valueOf(
                    evidence.path("status").asText()
                );
                LocalDate effectiveFrom = nullableDate(evidence.path("effective_from"));
                LocalDate effectiveTo = nullableDate(evidence.path("effective_to"));
                if (effectiveFrom != null && effectiveTo != null && effectiveFrom.isAfter(effectiveTo)) {
                    throw invalid("REFERENCE_EVIDENCE_SCHEMA_INVALID");
                }
                items.add(new ValidatedReferenceEvidenceBundle.Item(
                    evidenceId,
                    evidenceVersion,
                    ReferenceEvidenceType.valueOf(evidence.path("evidence_type").asText()),
                    evidence.path("authority").asText(),
                    evidence.path("title").asText(),
                    evidence.path("source_ref").asText(),
                    nullableText(evidence.path("source_url")),
                    nullableDate(evidence.path("source_date")),
                    effectiveFrom,
                    effectiveTo,
                    evidence.path("claim_scope").asText(),
                    evidence.path("claim_summary").asText(),
                    evidence.path("source_locator").asText(),
                    evidence.path("analysis_ref").asText(),
                    evidence.path("analysis_locator").asText(),
                    evidence.path("analysis_version").asText(),
                    status,
                    strings(evidence.path("workload_refs")),
                    evidence.path("content_digest").asText()
                ));
            }
            return new ValidatedReferenceEvidenceBundle(
                bundle.path("schema_version").asText(),
                bundle.path("bundle_id").asText(),
                bundle.path("bundle_version").asText(),
                OffsetDateTime.parse(bundle.path("snapshot_at").asText()),
                bundle.path("analysis_version").asText(),
                bundle.path("content_digest").asText(),
                items
            );
        } catch (ReferenceEvidenceException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ReferenceEvidenceException("REFERENCE_EVIDENCE_SCHEMA_INVALID", exception);
        }
    }

    private void verifyDigest(JsonNode value, String field) {
        ObjectNode target = ((ObjectNode) value).deepCopy();
        String received = target.remove(field).asText();
        if (!canonicalJson.digest(target).equals(received)) {
            throw invalid("REFERENCE_EVIDENCE_DIGEST_MISMATCH");
        }
    }

    private List<String> strings(JsonNode values) {
        List<String> result = new ArrayList<>();
        values.forEach(value -> result.add(value.asText()));
        return List.copyOf(result);
    }

    private String nullableText(JsonNode value) {
        return value.isNull() ? null : value.asText();
    }

    private LocalDate nullableDate(JsonNode value) {
        return value.isNull() ? null : LocalDate.parse(value.asText());
    }

    private Schema loadSchema() {
        try (InputStream input = getClass().getResourceAsStream(
            "/contracts/reference-evidence-bundle-v1.schema.json"
        )) {
            if (input == null) {
                throw new IllegalStateException("Reference Evidence schema is missing");
            }
            return SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
                .getSchema(mapper.readTree(input));
        } catch (IOException exception) {
            throw new IllegalStateException("Reference Evidence schema cannot be loaded", exception);
        }
    }

    private ReferenceEvidenceException invalid(String reasonCode) {
        return new ReferenceEvidenceException(reasonCode);
    }
}
