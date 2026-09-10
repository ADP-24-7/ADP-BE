package com.adp.gateway.evidence.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import com.adp.gateway.auth.domain.AdpRole;
import com.adp.gateway.auth.domain.AuthPrincipal;
import com.adp.gateway.auth.domain.AuthenticatedPrincipal;
import com.adp.gateway.auth.domain.PrincipalType;
import com.adp.gateway.evidence.application.ReferenceEvidenceCanonicalJson;
import com.adp.gateway.runtime.application.RuntimeExecutionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ReferenceEvidenceControllerTests {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private ReferenceEvidenceCanonicalJson canonicalJson;

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void runtimeExecutionServiceDoesNotDependOnReferenceEvidence() {
        assertThat(Stream.of(RuntimeExecutionService.class.getDeclaredFields())
            .map(field -> field.getType().getPackageName()))
            .noneMatch(packageName -> packageName.startsWith("com.adp.gateway.evidence"));
    }

    @Test
    void ingestsReplaysAndReadsScopedReferenceEvidenceWithoutChangingRuntimePolicy() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        String evidenceId = "REF-FSC-" + suffix;
        ObjectNode bundle = bundle(evidenceId, "customer_summary");
        long selectionCountBefore = count("policy.current_selection");
        long runtimeCountBefore = count("runtime.runtime_execution");

        mockMvc.perform(post("/api/admin/reference-evidence/bundles")
                .with(csrf())
                .with(authentication(principal(
                    "writer", "institution-a", Set.of("customer_summary"), AdpRole.PRIVILEGED_OPERATOR
                )))
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(bundle)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.evidenceCount").value(1))
            .andExpect(jsonPath("$.replayed").value(false));

        mockMvc.perform(post("/api/admin/reference-evidence/bundles")
                .with(csrf())
                .with(authentication(principal(
                    "writer", "institution-a", Set.of("customer_summary"), AdpRole.PRIVILEGED_OPERATOR
                )))
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(bundle)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.replayed").value(true));

        mockMvc.perform(get("/api/admin/reference-evidence")
                .with(authentication(principal(
                    "auditor", "institution-a", Set.of("customer_summary"), AdpRole.AUDITOR
                )))
                .param("workloadId", "customer_summary")
                .param("evidenceType", "POLICY_GUIDE"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.items[0].evidenceId").value(evidenceId))
            .andExpect(jsonPath("$.items[0].status").value("REFERENCE_ONLY"))
            .andExpect(jsonPath("$.items[0].analysisRef").value("analysis.ipynb"))
            .andExpect(jsonPath("$.items[0].claimSummary").exists());

        mockMvc.perform(get("/api/admin/reference-evidence/{id}/versions/1.0.0", evidenceId)
                .with(authentication(principal(
                    "other", "institution-b", Set.of("customer_summary"), AdpRole.AUDITOR
                ))))
            .andExpect(status().isNotFound());

        assertThat(count("policy.current_selection")).isEqualTo(selectionCountBefore);
        assertThat(count("runtime.runtime_execution")).isEqualTo(runtimeCountBefore);
        assertThat(countEvidencePolicyTables()).isZero();
    }

    @Test
    void rejectsDigestTamperingAndRuntimePolicyFields() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        ObjectNode tampered = bundle("REF-TAMPER-" + suffix, "customer_summary");
        ((ObjectNode) tampered.path("evidence").get(0)).put("claim_summary", "tampered");

        mockMvc.perform(post("/api/admin/reference-evidence/bundles")
                .with(csrf())
                .with(authentication(principal(
                    "writer", "institution-a", Set.of("customer_summary"), AdpRole.PRIVILEGED_OPERATOR
                )))
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(tampered)))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.reasonCode").value("REFERENCE_EVIDENCE_DIGEST_MISMATCH"));

        ObjectNode bound = bundle("REF-BOUND-" + suffix, "customer_summary");
        ObjectNode evidence = (ObjectNode) bound.path("evidence").get(0);
        evidence.withArray("policy_artifact_refs").add("runtime-policy-1");
        evidence.put("content_digest", evidenceDigest(evidence));
        bound.put("content_digest", bundleDigest(bound));

        mockMvc.perform(post("/api/admin/reference-evidence/bundles")
                .with(csrf())
                .with(authentication(principal(
                    "writer", "institution-a", Set.of("customer_summary"), AdpRole.PRIVILEGED_OPERATOR
                )))
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(bound)))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.reasonCode").value("REFERENCE_EVIDENCE_SCHEMA_INVALID"));

        ObjectNode verified = bundle("REF-STATUS-" + suffix, "customer_summary");
        ObjectNode verifiedEvidence = (ObjectNode) verified.path("evidence").get(0);
        verifiedEvidence.put("status", "VERIFIED");
        verifiedEvidence.put("content_digest", evidenceDigest(verifiedEvidence));
        verified.put("content_digest", bundleDigest(verified));

        mockMvc.perform(post("/api/admin/reference-evidence/bundles")
                .with(csrf())
                .with(authentication(principal(
                    "writer", "institution-a", Set.of("customer_summary"), AdpRole.PRIVILEGED_OPERATOR
                )))
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(verified)))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.reasonCode").value("REFERENCE_EVIDENCE_SCHEMA_INVALID"));
    }

    @Test
    void enforcesWriterRoleAndWorkloadScope() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        ObjectNode bundle = bundle("REF-SCOPE-" + suffix, "other-workload");

        mockMvc.perform(post("/api/admin/reference-evidence/bundles")
                .with(csrf())
                .with(authentication(principal(
                    "operator", "institution-a", Set.of("other-workload"), AdpRole.OPERATOR
                )))
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(bundle)))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/admin/reference-evidence/bundles")
                .with(csrf())
                .with(authentication(principal(
                    "writer", "institution-a", Set.of("customer_summary"), AdpRole.PRIVILEGED_OPERATOR
                )))
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(bundle)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.reasonCode").value("REFERENCE_EVIDENCE_FORBIDDEN"));
    }

    private ObjectNode bundle(String evidenceId, String workload) {
        ObjectNode evidence = mapper.createObjectNode();
        evidence.put("evidence_id", evidenceId);
        evidence.put("evidence_version", "1.0.0");
        evidence.put("evidence_type", "POLICY_GUIDE");
        evidence.put("authority", "Financial Services Commission");
        evidence.put("title", "Policy trend analysis");
        evidence.put("source_ref", "FSC-POLICY-MATERIALS");
        evidence.put("source_url", "https://www.fsc.go.kr/po010101");
        evidence.putNull("source_date");
        evidence.putNull("effective_from");
        evidence.putNull("effective_to");
        evidence.put("claim_scope", "AI policy trend");
        evidence.put("claim_summary", "Reference analysis only");
        evidence.put("source_locator", "official policy material index");
        evidence.put("analysis_ref", "analysis.ipynb");
        evidence.put("analysis_locator", "cell-1");
        evidence.put("analysis_version", "analysis/1.0.0");
        evidence.put("status", "REFERENCE_ONLY");
        evidence.putArray("workload_refs").add(workload);
        evidence.put("content_digest", evidenceDigest(evidence));

        ObjectNode bundle = mapper.createObjectNode();
        bundle.put("schema_version", "adp-reference-evidence-bundle/v1");
        bundle.put("bundle_id", "ADP-REFERENCE-" + evidenceId.substring(4));
        bundle.put("bundle_version", "1.0.0");
        bundle.put("snapshot_at", "2026-09-10T00:00:00+09:00");
        bundle.put("analysis_version", "adp-da/test");
        bundle.put("evidence_count", 1);
        bundle.set("evidence", mapper.createArrayNode().add(evidence));
        bundle.put("content_digest", bundleDigest(bundle));
        return bundle;
    }

    private String evidenceDigest(ObjectNode evidence) {
        ObjectNode target = evidence.deepCopy();
        target.remove("content_digest");
        return canonicalJson.digest(target);
    }

    private String bundleDigest(ObjectNode bundle) {
        ObjectNode target = bundle.deepCopy();
        target.remove("content_digest");
        return canonicalJson.digest(target);
    }

    private long count(String table) {
        return jdbcClient.sql("select count(*) from " + table).query(Long.class).single();
    }

    private long countEvidencePolicyTables() {
        return jdbcClient.sql("""
                select count(*) from information_schema.tables
                where table_schema = 'evidence'
                  and table_name = 'reference_evidence_policy_artifact'
                """).query(Long.class).single();
    }

    private AuthenticatedPrincipal principal(
        String id, String institutionId, Set<String> workloads, AdpRole role
    ) {
        AuthPrincipal principal = new AuthPrincipal(
            id, PrincipalType.USER, id, institutionId, false, workloads, Set.of(role)
        );
        return new AuthenticatedPrincipal(principal, List.of(() -> "ROLE_" + role.name()));
    }
}
