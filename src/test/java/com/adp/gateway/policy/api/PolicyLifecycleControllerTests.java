package com.adp.gateway.policy.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import java.util.Set;

import com.adp.gateway.auth.domain.AdpRole;
import com.adp.gateway.auth.domain.AuthPrincipal;
import com.adp.gateway.auth.domain.PrincipalType;
import com.adp.gateway.egress.domain.ExecutionPackType;
import com.adp.gateway.policy.application.PolicyLifecycleException;
import com.adp.gateway.policy.application.PolicyLifecycleService;
import com.adp.gateway.policy.domain.PolicyLayer;
import com.adp.gateway.policy.domain.PolicyLifecycleStage;
import com.adp.gateway.policy.domain.PolicyLifecycleTransitionReason;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
    "adp.local-fixtures.enabled=true",
    "adp.local-user-auth.enabled=true"
})
@AutoConfigureMockMvc
class PolicyLifecycleControllerTests {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private PolicyLifecycleService service;

    @Test
    void runsValidatedLifecycleWithMakerCheckerAndAppendOnlyEvidence() throws Exception {
        String artifactId = create("maker-1", "OPERATOR");
        transition(artifactId, "maker-1", "OPERATOR", "VALIDATED").andExpect(status().isOk());
        transition(artifactId, "maker-1", "OPERATOR", "CANDIDATE").andExpect(status().isOk());
        transition(artifactId, "maker-1", "OPERATOR", "REPLAY").andExpect(status().isOk());
        transition(artifactId, "maker-1", "OPERATOR", "SHADOW").andExpect(status().isOk());
        transition(artifactId, "checker-1", "PRIVILEGED_OPERATOR", "APPROVED").andExpect(status().isOk());
        transition(artifactId, "checker-1", "PRIVILEGED_OPERATOR", "ACTIVE")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.lifecycleStage").value("ACTIVE"))
            .andExpect(jsonPath("$.revision").value(6));

        mockMvc.perform(get("/api/admin/policy-lifecycle/{id}/versions/1.0.0", artifactId)
                .header("X-ADP-User-Id", "auditor-1")
                .header("X-ADP-User-Roles", "AUDITOR"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.artifactDigest").value("a".repeat(64)))
            .andExpect(jsonPath("$.lifecycleStage").value("ACTIVE"));

        Integer events = jdbcClient.sql("""
                select count(*) from policy.lifecycle_transition_event
                where artifact_id = :artifactId and artifact_version = '1.0.0'
                """).param("artifactId", artifactId).query(Integer.class).single();
        assertThat(events).isEqualTo(6);
    }

    @Test
    void rejectsInvalidTransitionAndOperatorApproval() throws Exception {
        String invalid = create("maker-invalid", "OPERATOR");
        transition(invalid, "checker-invalid", "PRIVILEGED_OPERATOR", "ACTIVE")
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.reasonCode").value("POLICY_LIFECYCLE_TRANSITION_INVALID"));

        String approval = create("maker-approval", "OPERATOR");
        advanceToShadow(approval, "maker-approval", "OPERATOR");
        transition(approval, "operator-2", "OPERATOR", "APPROVED")
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.reasonCode").value("POLICY_LIFECYCLE_FORBIDDEN"));
    }

    @Test
    void rejectsMakerApprovingOwnArtifact() throws Exception {
        String artifactId = create("maker-checker", "OPERATOR,PRIVILEGED_OPERATOR");
        advanceToShadow(artifactId, "maker-checker", "OPERATOR,PRIVILEGED_OPERATOR");

        transition(artifactId, "maker-checker", "OPERATOR,PRIVILEGED_OPERATOR", "APPROVED")
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.reasonCode").value("POLICY_LIFECYCLE_MAKER_CHECKER_VIOLATION"));
    }

    @Test
    void isolatesArtifactIdentityAndReadsByInstitutionAndWorkloadInDatabase() {
        String artifactId = "shared-policy-" + UUID.randomUUID().toString().substring(0, 8);
        AuthPrincipal institutionA = principal("operator-a", "institution-a", Set.of("workload-a"));
        AuthPrincipal institutionB = principal("operator-b", "institution-b", Set.of("workload-b"));

        service.create(institutionA, artifactId, "1.0.0", "a".repeat(64), PolicyLayer.WORKLOAD,
            ExecutionPackType.AI, "workload-a", "CUSTOMER_SUPPORT");
        service.create(institutionB, artifactId, "1.0.0", "b".repeat(64), PolicyLayer.WORKLOAD,
            ExecutionPackType.AI, "workload-b", "CUSTOMER_SUPPORT");

        assertThat(service.load(institutionA, artifactId, "1.0.0").artifactDigest()).isEqualTo("a".repeat(64));
        assertThat(service.load(institutionB, artifactId, "1.0.0").artifactDigest()).isEqualTo("b".repeat(64));
        assertThatThrownBy(() -> service.load(
            principal("operator-a", "institution-a", Set.of("workload-b")), artifactId, "1.0.0"
        )).isInstanceOf(PolicyLifecycleException.class)
            .extracting(exception -> ((PolicyLifecycleException) exception).reasonCode())
            .isEqualTo("POLICY_LIFECYCLE_ARTIFACT_NOT_FOUND");
        assertThatThrownBy(() -> service.load(
            principal("operator-c", "institution-c", Set.of("workload-a")), artifactId, "1.0.0"
        )).isInstanceOf(PolicyLifecycleException.class)
            .extracting(exception -> ((PolicyLifecycleException) exception).reasonCode())
            .isEqualTo("POLICY_LIFECYCLE_ARTIFACT_NOT_FOUND");

        assertThatThrownBy(() -> service.transition(
            principal("operator-c", "institution-c", Set.of("workload-a")), artifactId, "1.0.0",
            PolicyLifecycleStage.VALIDATED, PolicyLifecycleTransitionReason.VALIDATION_PASSED
        )).isInstanceOf(PolicyLifecycleException.class);
        assertThatThrownBy(() -> service.transition(
            principal("operator-a", "institution-a", Set.of("workload-b")), artifactId, "1.0.0",
            PolicyLifecycleStage.VALIDATED, PolicyLifecycleTransitionReason.VALIDATION_PASSED
        )).isInstanceOf(PolicyLifecycleException.class);

        Integer events = jdbcClient.sql("""
                select count(*) from policy.lifecycle_transition_event where artifact_id = :artifactId
                """).param("artifactId", artifactId).query(Integer.class).single();
        assertThat(events).isZero();
    }

    @Test
    void rejectsUnsupportedSaasPackBeforePersistence() throws Exception {
        String artifactId = "saas-policy-" + UUID.randomUUID().toString().substring(0, 8);

        mockMvc.perform(post("/api/admin/policy-lifecycle")
                .header("X-ADP-User-Id", "maker-saas")
                .header("X-ADP-User-Roles", "OPERATOR")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"artifactId":"%s","artifactVersion":"1.0.0","artifactDigest":"%s",
                     "policyLayer":"WORKLOAD","executionPack":"SAAS",
                     "workloadId":"customer_summary","purposeCode":"CUSTOMER_SUPPORT"}
                    """.formatted(artifactId, "a".repeat(64))))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.reasonCode").value("POLICY_LIFECYCLE_ARTIFACT_INVALID"));

        Integer count = jdbcClient.sql("""
                select count(*) from policy.lifecycle_artifact where artifact_id = :artifactId
                """).param("artifactId", artifactId).query(Integer.class).single();
        assertThat(count).isZero();
    }

    private String create(String actor, String roles) throws Exception {
        String artifactId = "policy-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        mockMvc.perform(post("/api/admin/policy-lifecycle")
                .header("X-ADP-User-Id", actor)
                .header("X-ADP-User-Roles", roles)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"artifactId":"%s","artifactVersion":"1.0.0","artifactDigest":"%s",
                     "policyLayer":"WORKLOAD","executionPack":"AI",
                     "workloadId":"customer_summary","purposeCode":"CUSTOMER_SUPPORT"}
                    """.formatted(artifactId, "a".repeat(64))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.lifecycleStage").value("DRAFT"));
        return artifactId;
    }

    private void advanceToShadow(String artifactId, String actor, String roles) throws Exception {
        transition(artifactId, actor, roles, "VALIDATED").andExpect(status().isOk());
        transition(artifactId, actor, roles, "CANDIDATE").andExpect(status().isOk());
        transition(artifactId, actor, roles, "REPLAY").andExpect(status().isOk());
        transition(artifactId, actor, roles, "SHADOW").andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.ResultActions transition(
        String artifactId, String actor, String roles, String target
    ) throws Exception {
        return mockMvc.perform(post("/api/admin/policy-lifecycle/{id}/versions/1.0.0/transitions", artifactId)
            .header("X-ADP-User-Id", actor)
            .header("X-ADP-User-Roles", roles)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"targetStage":"%s","reasonCode":"%s"}
                """.formatted(target, reason(target))));
    }

    private String reason(String target) {
        return switch (target) {
            case "VALIDATED" -> "VALIDATION_PASSED";
            case "CANDIDATE" -> "CANDIDATE_PROMOTED";
            case "REPLAY" -> "REPLAY_PASSED";
            case "SHADOW" -> "SHADOW_PASSED";
            case "APPROVED" -> "APPROVAL_GRANTED";
            case "ACTIVE" -> "ACTIVATION_APPROVED";
            case "REVIEW" -> "REVIEW_OPENED";
            case "ROLLED_BACK" -> "ROLLBACK_APPROVED";
            default -> throw new IllegalArgumentException("Unknown target");
        };
    }

    private AuthPrincipal principal(String id, String institutionId, Set<String> workloads) {
        return new AuthPrincipal(
            id, PrincipalType.USER, id, institutionId, false, workloads, Set.of(AdpRole.OPERATOR)
        );
    }
}
