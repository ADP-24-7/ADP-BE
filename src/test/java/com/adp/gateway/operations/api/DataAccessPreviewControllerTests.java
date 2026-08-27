package com.adp.gateway.operations.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
    "adp.local-fixtures.enabled=true",
    "adp.data-access-preview.enabled=true"
})
@AutoConfigureMockMvc
class DataAccessPreviewControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void retrievesOnlyAllowedFieldsWithinProfileLimits() throws Exception {
        mockMvc.perform(post("/api/runtime/data-access/preview")
                .header("X-Request-Id", "req_data_access_ok")
                .header("X-Trace-Id", "trace_data_access_ok")
                .header("X-ADP-API-Key", "local-dev-api-key")
                .contentType("application/json")
                .content("""
                    {
                      "workloadId": "customer_summary",
                      "purpose": "CUSTOMER_SUPPORT",
                      "subject": "customer:customer-100"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.dataAccessId").exists())
            .andExpect(jsonPath("$.workloadId").value("customer_summary"))
            .andExpect(jsonPath("$.profileId").value("profile_customer_summary_support"))
            .andExpect(jsonPath("$.rowLimit").value(2))
            .andExpect(jsonPath("$.selectedFields[*].fieldName", hasItem("customer_id")))
            .andExpect(jsonPath("$.selectedFields[*].fieldName", hasItem("balance")))
            .andExpect(jsonPath("$.selectedFields[*].fieldName", not(hasItem("resident_registration_number"))))
            .andExpect(jsonPath("$.selectedFields[*].fieldName", not(hasItem("account_number"))))
            .andExpect(jsonPath("$.records[*].fields.account_number").doesNotExist())
            .andExpect(jsonPath("$.records[*].fields.resident_registration_number").doesNotExist())
            .andExpect(jsonPath("$.records[?(@.datasetName == 'transaction')]").isArray());

        Integer transactionRows = jdbcClient.sql("""
                select count(*)
                from data_access_event
                where request_id = 'req_data_access_ok'
                  and trace_id = 'trace_data_access_ok'
                  and workload_id = 'customer_summary'
                  and selected_fields not like '%account_number%'
                  and selected_fields not like '%resident_registration_number%'
                """)
            .query(Integer.class)
            .single();

        assertThat(transactionRows).isEqualTo(1);
    }

    @Test
    void deniesDifferentSubjectBeforeRetrieval() throws Exception {
        mockMvc.perform(post("/api/runtime/data-access/preview")
                .header("X-Request-Id", "req_data_access_subject_denied")
                .header("X-Trace-Id", "trace_data_access_subject_denied")
                .header("X-ADP-API-Key", "local-dev-api-key")
                .contentType("application/json")
                .content("""
                    {
                      "workloadId": "customer_summary",
                      "purpose": "CUSTOMER_SUPPORT",
                      "subject": "customer:customer-999"
                    }
                    """))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.reasonCode").value("AUTHORIZATION_DENIED"));
    }

    @Test
    void deniesPurposeWithoutGrantBeforeRetrieval() throws Exception {
        mockMvc.perform(post("/api/runtime/data-access/preview")
                .header("X-Request-Id", "req_data_access_purpose_denied")
                .header("X-Trace-Id", "trace_data_access_purpose_denied")
                .header("X-ADP-API-Key", "local-dev-api-key")
                .contentType("application/json")
                .content("""
                    {
                      "workloadId": "customer_summary",
                      "purpose": "MODEL_TRAINING",
                      "subject": "customer:customer-100"
                    }
                    """))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.reasonCode").value("AUTHORIZATION_DENIED"));
    }

    @Test
    void deniesUnregisteredRetrievalProfile() throws Exception {
        jdbcClient.sql("""
                insert into auth_principal_workload (principal_id, workload_id)
                values ('svc_local_runtime', 'unknown_workload')
                on conflict (principal_id, workload_id) do nothing
                """).update();
        jdbcClient.sql("""
                insert into auth_subject_grant (
                    principal_id, workload_id, action_name, purpose, subject_type, subject_id
                ) values (
                    'svc_local_runtime', 'unknown_workload', 'RUNTIME_EXECUTE', 'CUSTOMER_SUPPORT',
                    'customer', 'customer-100'
                ) on conflict (principal_id, workload_id, action_name, purpose, subject_type, subject_id) do nothing
                """).update();

        mockMvc.perform(post("/api/runtime/data-access/preview")
                .header("X-Request-Id", "req_data_access_profile_denied")
                .header("X-Trace-Id", "trace_data_access_profile_denied")
                .header("X-ADP-API-Key", "local-dev-api-key")
                .contentType("application/json")
                .content("""
                    {
                      "workloadId": "unknown_workload",
                      "purpose": "CUSTOMER_SUPPORT",
                      "subject": "customer:customer-100"
                    }
                    """))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.reasonCode").value("DATA_ACCESS_DENIED"));
    }
}
