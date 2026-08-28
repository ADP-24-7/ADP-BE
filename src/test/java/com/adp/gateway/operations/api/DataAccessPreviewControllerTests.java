package com.adp.gateway.operations.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
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
            .andExpect(jsonPath("$.datasetScopes[?(@.datasetName == 'customer' && @.rowLimit == 1)]", hasSize(1)))
            .andExpect(jsonPath("$.datasetScopes[?(@.datasetName == 'account' && @.rowLimit == 2)]", hasSize(1)))
            .andExpect(jsonPath("$.datasetScopes[?(@.datasetName == 'transaction' && @.rowLimit == 2)]", hasSize(1)))
            .andExpect(jsonPath("$.selectedFields[*].fieldName", hasItem("customer_id")))
            .andExpect(jsonPath("$.selectedFields[*].fieldName", hasItem("balance")))
            .andExpect(jsonPath("$.selectedFields[*].fieldName", not(hasItem("resident_registration_number"))))
            .andExpect(jsonPath("$.selectedFields[*].fieldName", not(hasItem("account_number"))))
            .andExpect(jsonPath("$.records[*].fields.account_number").doesNotExist())
            .andExpect(jsonPath("$.records[*].fields.resident_registration_number").doesNotExist())
            .andExpect(jsonPath("$.records[?(@.datasetName == 'customer')]", hasSize(1)))
            .andExpect(jsonPath("$.records[?(@.datasetName == 'account')]", hasSize(1)))
            .andExpect(jsonPath("$.records[?(@.datasetName == 'transaction')]", hasSize(2)));

        Integer transactionRows = jdbcClient.sql("""
                select count(*)
                from data_access_event
                where request_id = 'req_data_access_ok'
                  and trace_id = 'trace_data_access_ok'
                  and workload_id = 'customer_summary'
                  and subject_ref_digest is not null
                  and selected_fields not like '%account_number%'
                  and selected_fields not like '%resident_registration_number%'
                """)
            .query(Integer.class)
            .single();

        assertThat(transactionRows).isEqualTo(1);

        Integer rawSubjectAuditRows = jdbcClient.sql("""
                select count(*)
                from data_access_event
                where request_id = 'req_data_access_ok'
                  and subject_ref_digest = 'customer-100'
                """)
            .query(Integer.class)
            .single();

        assertThat(rawSubjectAuditRows).isZero();
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

    @Test
    void returnsEmptyResultWhenAuthorizedSubjectHasNoRows() throws Exception {
        jdbcClient.sql("""
                insert into auth_subject_grant (
                    principal_id, workload_id, action_name, purpose, subject_type, subject_id
                ) values (
                    'svc_local_runtime', 'customer_summary', 'RUNTIME_EXECUTE', 'CUSTOMER_SUPPORT',
                    'customer', 'customer-404'
                ) on conflict (principal_id, workload_id, action_name, purpose, subject_type, subject_id) do nothing
                """).update();

        mockMvc.perform(post("/api/runtime/data-access/preview")
                .header("X-Request-Id", "req_data_access_empty")
                .header("X-Trace-Id", "trace_data_access_empty")
                .header("X-ADP-API-Key", "local-dev-api-key")
                .contentType("application/json")
                .content("""
                    {
                      "workloadId": "customer_summary",
                      "purpose": "CUSTOMER_SUPPORT",
                      "subject": "customer:customer-404"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.rowCount").value(0))
            .andExpect(jsonPath("$.records", hasSize(0)));
    }

    @Test
    void skipsDatasetWhenProfileHasNoAllowedFieldsForDataset() throws Exception {
        jdbcClient.sql("""
                insert into retrieval_profile (
                    profile_id, workload_id, purpose, subject_type, enabled
                ) values (
                    'profile_customer_summary_without_transactions',
                    'customer_summary',
                    'CUSTOMER_SUPPORT_NO_TRANSACTIONS',
                    'customer',
                    true
                ) on conflict (profile_id) do nothing
                """).update();
        jdbcClient.sql("""
                insert into retrieval_profile_dataset (profile_id, dataset_name, row_limit, time_window_days)
                values
                    ('profile_customer_summary_without_transactions', 'customer', 1, null),
                    ('profile_customer_summary_without_transactions', 'account', 2, null),
                    ('profile_customer_summary_without_transactions', 'transaction', 2, 90)
                on conflict (profile_id, dataset_name) do nothing
                """).update();
        jdbcClient.sql("""
                insert into retrieval_profile_field (profile_id, dataset_name, field_name, data_class)
                values
                    (
                        'profile_customer_summary_without_transactions',
                        'customer',
                        'customer_id',
                        'CUSTOMER_IDENTIFIER'
                    ),
                    (
                        'profile_customer_summary_without_transactions',
                        'account',
                        'account_id',
                        'ACCOUNT_IDENTIFIER'
                    )
                on conflict (profile_id, dataset_name, field_name) do nothing
                """).update();
        jdbcClient.sql("""
                insert into auth_subject_grant (
                    principal_id, workload_id, action_name, purpose, subject_type, subject_id
                ) values (
                    'svc_local_runtime', 'customer_summary', 'RUNTIME_EXECUTE',
                    'CUSTOMER_SUPPORT_NO_TRANSACTIONS', 'customer', 'customer-100'
                ) on conflict (principal_id, workload_id, action_name, purpose, subject_type, subject_id) do nothing
                """).update();

        mockMvc.perform(post("/api/runtime/data-access/preview")
                .header("X-Request-Id", "req_data_access_no_transactions")
                .header("X-Trace-Id", "trace_data_access_no_transactions")
                .header("X-ADP-API-Key", "local-dev-api-key")
                .contentType("application/json")
                .content("""
                    {
                      "workloadId": "customer_summary",
                      "purpose": "CUSTOMER_SUPPORT_NO_TRANSACTIONS",
                      "subject": "customer:customer-100"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.records[?(@.datasetName == 'transaction')]", hasSize(0)))
            .andExpect(jsonPath("$.records[*].fields.transaction_id").doesNotExist())
            .andExpect(jsonPath("$.records[*].fields.amount").doesNotExist());
    }

    @Test
    void deniesInvalidTransactionTimeWindowWithoutServerError() throws Exception {
        insertProfile("profile_invalid_transaction_window", "CUSTOMER_SUPPORT_INVALID_WINDOW");
        jdbcClient.sql("""
                insert into retrieval_profile_dataset (profile_id, dataset_name, row_limit, time_window_days)
                values ('profile_invalid_transaction_window', 'transaction', 2, null)
                on conflict (profile_id, dataset_name) do nothing
                """).update();
        jdbcClient.sql("""
                insert into retrieval_profile_field (profile_id, dataset_name, field_name, data_class)
                values (
                    'profile_invalid_transaction_window',
                    'transaction',
                    'transaction_id',
                    'TRANSACTION_IDENTIFIER'
                )
                on conflict (profile_id, dataset_name, field_name) do nothing
                """).update();
        insertGrant("CUSTOMER_SUPPORT_INVALID_WINDOW", "customer-100");

        mockMvc.perform(post("/api/runtime/data-access/preview")
                .header("X-Request-Id", "req_invalid_time_window")
                .header("X-Trace-Id", "trace_invalid_time_window")
                .header("X-ADP-API-Key", "local-dev-api-key")
                .contentType("application/json")
                .content("""
                    {
                      "workloadId": "customer_summary",
                      "purpose": "CUSTOMER_SUPPORT_INVALID_WINDOW",
                      "subject": "customer:customer-100"
                    }
                    """))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.reasonCode").value("DATA_ACCESS_DENIED"));
    }

    @Test
    void deniesProfileFieldThatIsNotInServerCatalog() throws Exception {
        insertProfile("profile_unsupported_field", "CUSTOMER_SUPPORT_UNSUPPORTED_FIELD");
        jdbcClient.sql("""
                insert into retrieval_profile_dataset (profile_id, dataset_name, row_limit, time_window_days)
                values ('profile_unsupported_field', 'customer', 1, null)
                on conflict (profile_id, dataset_name) do nothing
                """).update();
        jdbcClient.sql("""
                insert into retrieval_profile_field (profile_id, dataset_name, field_name, data_class)
                values (
                    'profile_unsupported_field',
                    'customer',
                    'customer_name',
                    'CUSTOMER_IDENTIFIER'
                )
                on conflict (profile_id, dataset_name, field_name) do nothing
                """).update();
        insertGrant("CUSTOMER_SUPPORT_UNSUPPORTED_FIELD", "customer-100");

        mockMvc.perform(post("/api/runtime/data-access/preview")
                .header("X-Request-Id", "req_unsupported_field")
                .header("X-Trace-Id", "trace_unsupported_field")
                .header("X-ADP-API-Key", "local-dev-api-key")
                .contentType("application/json")
                .content("""
                    {
                      "workloadId": "customer_summary",
                      "purpose": "CUSTOMER_SUPPORT_UNSUPPORTED_FIELD",
                      "subject": "customer:customer-100"
                    }
                    """))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.reasonCode").value("DATA_ACCESS_DENIED"));
    }

    @Test
    void deniesProfileFieldWhenRuntimeDataClassDoesNotMatchServerCatalog() throws Exception {
        insertProfile("profile_data_class_mismatch", "CUSTOMER_SUPPORT_DATA_CLASS_MISMATCH");
        jdbcClient.sql("""
                insert into retrieval_profile_dataset (profile_id, dataset_name, row_limit, time_window_days)
                values ('profile_data_class_mismatch', 'account', 1, null)
                on conflict (profile_id, dataset_name) do nothing
                """).update();
        jdbcClient.sql("""
                insert into retrieval_profile_field (profile_id, dataset_name, field_name, data_class)
                values (
                    'profile_data_class_mismatch',
                    'account',
                    'balance',
                    'BUSINESS_METADATA'
                )
                on conflict (profile_id, dataset_name, field_name) do nothing
                """).update();
        insertGrant("CUSTOMER_SUPPORT_DATA_CLASS_MISMATCH", "customer-100");

        mockMvc.perform(post("/api/runtime/data-access/preview")
                .header("X-Request-Id", "req_data_class_mismatch")
                .header("X-Trace-Id", "trace_data_class_mismatch")
                .header("X-ADP-API-Key", "local-dev-api-key")
                .contentType("application/json")
                .content("""
                    {
                      "workloadId": "customer_summary",
                      "purpose": "CUSTOMER_SUPPORT_DATA_CLASS_MISMATCH",
                      "subject": "customer:customer-100"
                    }
                    """))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.reasonCode").value("DATA_ACCESS_DENIED"));
    }

    private void insertProfile(String profileId, String purpose) {
        jdbcClient.sql("""
                insert into retrieval_profile (
                    profile_id, workload_id, purpose, subject_type, enabled
                ) values (
                    :profileId,
                    'customer_summary',
                    :purpose,
                    'customer',
                    true
                ) on conflict (profile_id) do nothing
                """)
            .param("profileId", profileId)
            .param("purpose", purpose)
            .update();
    }

    private void insertGrant(String purpose, String subjectId) {
        jdbcClient.sql("""
                insert into auth_subject_grant (
                    principal_id, workload_id, action_name, purpose, subject_type, subject_id
                ) values (
                    'svc_local_runtime', 'customer_summary', 'RUNTIME_EXECUTE',
                    :purpose, 'customer', :subjectId
                ) on conflict (principal_id, workload_id, action_name, purpose, subject_type, subject_id) do nothing
                """)
            .param("purpose", purpose)
            .param("subjectId", subjectId)
            .update();
    }
}
