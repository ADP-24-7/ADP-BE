package com.adp.gateway.operations.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;

import com.adp.gateway.operations.application.AdminIdentityNotFoundException;
import com.adp.gateway.operations.application.AdminIdentityReadPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
                .value(2))
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
    void nonOperationsRoleCannotReadIdentities() throws Exception {
        mockMvc.perform(get("/api/admin/identities")
                .header("X-ADP-User-Id", "developer-local")
                .header("X-ADP-User-Roles", "DEVELOPER"))
            .andExpect(status().isForbidden());
    }
}
