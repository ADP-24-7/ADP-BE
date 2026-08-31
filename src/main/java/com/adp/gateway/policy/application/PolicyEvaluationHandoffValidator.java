package com.adp.gateway.policy.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.adp.gateway.policy.domain.AnalysisStatus;
import org.springframework.stereotype.Component;

@Component
public class PolicyEvaluationHandoffValidator {

    private static final Set<String> SUPPORTED_SCHEMA_VERSIONS = Set.of("v1");
    private static final Set<String> SUPPORTED_HANDOFF_DISPOSITIONS = Set.of(
        "candidate_handoff",
        "requires_evaluation",
        "hold",
        "reject",
        "no_runtime_action"
    );
    private static final Set<String> SUPPORTED_MAPPING_STATUSES = Set.of("mapped", "unmapped", "tbd");

    public void validate(PolicyEvaluationHandoffArtifact artifact) {
        require(artifact != null, "handoff artifact is required");
        require(SUPPORTED_SCHEMA_VERSIONS.contains(artifact.schemaVersion()), "unsupported schema_version");
        require(hasText(artifact.artifactId()), "artifact_id is required");
        require(hasText(artifact.artifactVersion()), "artifact_version is required");
        require(SUPPORTED_HANDOFF_DISPOSITIONS.contains(artifact.policyAction()), "unsupported policy_action");
        requireStatus(artifact.analysisStatus(), "analysis_status");
        requireRefs(artifact.matchedPolicyRefs(), "matched_policy_refs");
        requireRefs(artifact.matchedRuleRefs(), "matched_rule_refs");
        requireRefs(artifact.requirementRefs(), "requirement_refs");
        require(artifact.digest() != null, "digest is required");
        require("sha256".equals(artifact.digest().algorithm()), "unsupported digest algorithm");
        require(hasText(artifact.digest().value()), "digest value is required");
        require(artifact.applicability() != null, "applicability is required");
        requireStatus(artifact.applicability().status(), "applicability.status");
        require(artifact.runtimeBinding() != null, "runtime_binding is required");
        require(
            SUPPORTED_MAPPING_STATUSES.contains(artifact.runtimeBinding().mappingStatus()),
            "unsupported runtime_binding.mapping_status"
        );
    }

    public void validateDigest(PolicyEvaluationHandoffArtifact artifact, String canonicalPayload) {
        validate(artifact);
        require(hasText(canonicalPayload), "canonical payload is required");
        require(artifact.digest().value().equals(sha256(canonicalPayload)), "digest mismatch");
    }

    private void requireRefs(List<HandoffReference> refs, String fieldName) {
        require(refs != null && !refs.isEmpty(), fieldName + " is required");
        refs.forEach(ref -> {
            require(hasText(ref.refId()), fieldName + ".ref_id is required");
            require(hasText(ref.refType()), fieldName + ".ref_type is required");
        });
    }

    private void requireStatus(String status, String fieldName) {
        try {
            AnalysisStatus.valueOf(status.toUpperCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("unsupported " + fieldName, exception);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm is not available", exception);
        }
    }
}
