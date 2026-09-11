package com.adp.gateway.operations.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;
import java.util.UUID;

import com.adp.gateway.operations.application.AdminIdentityNotFoundException;
import com.adp.gateway.operations.application.AdminIdentityReadPort;
import com.adp.gateway.operations.domain.WorkloadRegistryStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
    "adp.local-fixtures.enabled=true",
    "adp.local-user-auth.enabled=true"
})
@AutoConfigureMockMvc
class AdminIdentityControllerTests {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AdminIdentityReadPort readPort;

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void operatorSearchesInstitutionIdentitiesWithoutSecretMaterial() throws Exception {
        mockMvc.perform(get("/api/admin/identities")
                .header("X-ADP-User-Id", "operator-local")
                .header("X-ADP-User-Roles", "OPERATOR")
                .param("query", "svc_local_runtime")
                .param("role", "RUNTIME_EXECUTOR")
                .param("workloadId", "customer_summary"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].principalId").value("svc_local_runtime"))
            .andExpect(jsonPath("$.items[0].institutionId").value("institution_local"))
            .andExpect(jsonPath("$.items[0].roles[0]").exists())
            .andExpect(jsonPath("$.items[0].workloadIds[0]").exists())
            .andExpect(jsonPath("$.items[0].enabledApiKeyCount").value(1))
            .andExpect(jsonPath("$.items[0].keyHash").doesNotExist())
            .andExpect(jsonPath("$.items[0].apiKey").doesNotExist());
    }

    @Test
    void auditorLoadsPurposePermissionWithoutSubjectIdentity() throws Exception {
        mockMvc.perform(get("/api/admin/identities/svc_local_runtime")
                .header("X-ADP-User-Id", "auditor-local")
                .header("X-ADP-User-Roles", "AUDITOR"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.identity.principalId").value("svc_local_runtime"))
            .andExpect(jsonPath("$.permissions[?(@.workloadId == 'customer_summary')].actionName")
                .value("RUNTIME_EXECUTE"))
            .andExpect(jsonPath("$.permissions[?(@.workloadId == 'customer_summary')].purpose")
                .value("CUSTOMER_SUPPORT"))
            .andExpect(jsonPath("$.permissions[?(@.workloadId == 'customer_summary')].subjectGrantCount")
                .value(4))
            .andExpect(jsonPath("$.permissions[?(@.workloadId == 'customer_summary')].workloadRegistryStatus")
                .value("ENABLED"))
            .andExpect(jsonPath("$.permissions[?(@.workloadId == 'workload_local')].workloadRegistryStatus")
                .value("UNRESOLVED"))
            .andExpect(jsonPath("$.permissions[0].subjectId").doesNotExist());
    }

    @Test
    void readPortRestrictsVisibleWorkloadsAndCrossInstitutionDetail() {
        var page = readPort.search(
            "institution_local", Set.of("customer_summary"), null, null,
            null, true, "svc_local_runtime", 0, 10
        );

        assertThat(page.items()).singleElement().satisfies(identity ->
            assertThat(identity.workloadIds()).containsExactly("customer_summary")
        );
        assertThat(readPort.load(
            "svc_local_runtime", "institution_local", Set.of("customer_summary")
        ).permissions()).allMatch(permission -> permission.workloadId().equals("customer_summary"));
        assertThatThrownBy(() -> readPort.load(
            "svc_local_runtime", "institution_other", Set.of("*")
        )).isInstanceOf(AdminIdentityNotFoundException.class);
    }

    @Test
    void workloadScopeRestrictsIdentityRowsCountAndDetail() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String visiblePrincipal = "scope-visible-" + suffix;
        String hiddenPrincipal = "scope-hidden-" + suffix;
        jdbcClient.sql("""
                insert into auth_principal (
                    principal_id, principal_type, display_name, institution_id,
                    subject_authorization_required, enabled
                ) values
                    (:visiblePrincipal, 'SERVICE', :visiblePrincipal, 'institution_local', false, true),
                    (:hiddenPrincipal, 'SERVICE', :hiddenPrincipal, 'institution_local', false, true)
                """)
            .param("visiblePrincipal", visiblePrincipal)
            .param("hiddenPrincipal", hiddenPrincipal)
            .update();
        jdbcClient.sql("""
                insert into auth_principal_workload (principal_id, workload_id) values
                    (:visiblePrincipal, 'customer_summary'),
                    (:hiddenPrincipal, 'payment_settlement')
                """)
            .param("visiblePrincipal", visiblePrincipal)
            .param("hiddenPrincipal", hiddenPrincipal)
            .update();

        var scoped = readPort.search(
            "institution_local", Set.of("customer_summary"), null, null,
            null, true, suffix, 0, 10
        );
        assertThat(scoped.items()).extracting(identity -> identity.principalId())
            .containsExactly(visiblePrincipal);
        assertThat(scoped.totalElements()).isEqualTo(1);
        assertThatThrownBy(() -> readPort.load(
            hiddenPrincipal, "institution_local", Set.of("customer_summary")
        )).isInstanceOf(AdminIdentityNotFoundException.class);

        var emptyScope = readPort.search(
            "institution_local", Set.of(), null, null, null, true, suffix, 0, 10
        );
        assertThat(emptyScope.items()).isEmpty();
        assertThat(emptyScope.totalElements()).isZero();
        assertThatThrownBy(() -> readPort.load(
            visiblePrincipal, "institution_local", Set.of()
        )).isInstanceOf(AdminIdentityNotFoundException.class);
    }

    @Test
    void distinguishesDisabledAndUnresolvedWorkloadRegistryStatus() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String principalId = "registry-status-" + suffix;
        String workloadId = "disabled-workload-" + suffix;
        jdbcClient.sql("""
                insert into workload_registry (workload_id, display_name, description, enabled)
                values (:workloadId, 'Disabled workload', 'Identity status contract test', false)
                """)
            .param("workloadId", workloadId)
            .update();
        jdbcClient.sql("""
                insert into auth_principal (
                    principal_id, principal_type, display_name, institution_id,
                    subject_authorization_required, enabled
                ) values (:principalId, 'SERVICE', :principalId, 'institution_local', true, true)
                """)
            .param("principalId", principalId)
            .update();
        jdbcClient.sql("insert into auth_principal_workload (principal_id, workload_id) values (:principalId, :workloadId)")
            .param("principalId", principalId)
            .param("workloadId", workloadId)
            .update();
        jdbcClient.sql("""
                insert into auth_subject_grant (
                    principal_id, workload_id, action_name, purpose, subject_type, subject_id
                ) values (:principalId, :workloadId, 'RUNTIME_EXECUTE', 'TEST', 'customer', 'hidden-subject')
                """)
            .param("principalId", principalId)
            .param("workloadId", workloadId)
            .update();

        assertThat(readPort.load(
            principalId, "institution_local", Set.of(workloadId)
        ).permissions()).singleElement().satisfies(permission ->
            assertThat(permission.workloadRegistryStatus()).isEqualTo(WorkloadRegistryStatus.DISABLED)
        );
        assertThat(readPort.load(
            "svc_local_runtime", "institution_local", Set.of("workload_local")
        ).permissions()).allMatch(permission ->
            permission.workloadRegistryStatus() == WorkloadRegistryStatus.UNRESOLVED
        );
    }

    @Test
    void nonOperationsRoleCannotReadIdentities() throws Exception {
        mockMvc.perform(get("/api/admin/identities")
                .header("X-ADP-User-Id", "developer-local")
                .header("X-ADP-User-Roles", "DEVELOPER"))
            .andExpect(status().isForbidden());
    }
}
