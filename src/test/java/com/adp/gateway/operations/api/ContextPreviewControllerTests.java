package com.adp.gateway.operations.api;

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
    "adp.context-preview.enabled=true"
})
@AutoConfigureMockMvc
class ContextPreviewControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void buildsCanonicalContextAndDetectionResultFromRetrieval() throws Exception {
        mockMvc.perform(post("/api/runtime/context/preview")
                .header("X-Request-Id", "req_context_ok")
                .header("X-Trace-Id", "trace_context_ok")
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
            .andExpect(jsonPath("$.schemaVersion").value("canonical-context/v1"))
            .andExpect(jsonPath("$.contextId").exists())
            .andExpect(jsonPath("$.dataAccessId").exists())
            .andExpect(jsonPath("$.subjectRefDigest").isString())
            .andExpect(jsonPath("$.contextDigest").isString())
            .andExpect(jsonPath("$.fields[*].fieldName", hasItem("customer_id")))
            .andExpect(jsonPath("$.fields[*].fieldName", hasItem("balance")))
            .andExpect(jsonPath("$.fields[*].fieldName", not(hasItem("account_number"))))
            .andExpect(jsonPath("$.fields[*].value").doesNotExist())
            .andExpect(jsonPath("$.fields[*].valueDigest").isArray())
            .andExpect(jsonPath("$.detection.detectorVersion").value("regex-dev-1"))
            .andExpect(jsonPath("$.detection.findings", hasSize(0)));
    }

    @Test
    void detectsLabeledSensitiveFixtureThroughRetrievalContextAndDetector() throws Exception {
        jdbcClient.sql("""
                insert into retrieval_profile (
                    profile_id, workload_id, purpose, subject_type, enabled
                ) values (
                    'profile_customer_summary_sensitive_detection_contract',
                    'customer_summary',
                    'SENSITIVE_DETECTION_CONTRACT',
                    'customer',
                    true
                ) on conflict (profile_id) do nothing
                """).update();
        jdbcClient.sql("""
                insert into retrieval_profile_dataset (profile_id, dataset_name, row_limit, time_window_days)
                values
                    ('profile_customer_summary_sensitive_detection_contract', 'customer', 1, null),
                    ('profile_customer_summary_sensitive_detection_contract', 'account', 1, null)
                on conflict (profile_id, dataset_name) do nothing
                """).update();
        jdbcClient.sql("""
                insert into retrieval_profile_field (profile_id, dataset_name, field_name, data_class)
                values
                    (
                        'profile_customer_summary_sensitive_detection_contract',
                        'customer',
                        'phone_number',
                        'CUSTOMER_IDENTIFIER'
                    ),
                    (
                        'profile_customer_summary_sensitive_detection_contract',
                        'customer',
                        'email',
                        'CUSTOMER_IDENTIFIER'
                    ),
                    (
                        'profile_customer_summary_sensitive_detection_contract',
                        'customer',
                        'resident_registration_number',
                        'CUSTOMER_IDENTIFIER'
                    ),
                    (
                        'profile_customer_summary_sensitive_detection_contract',
                        'account',
                        'account_number',
                        'ACCOUNT_IDENTIFIER'
                    )
                on conflict (profile_id, dataset_name, field_name) do nothing
                """).update();
        jdbcClient.sql("""
                insert into auth_subject_grant (
                    principal_id, workload_id, action_name, purpose, subject_type, subject_id
                ) values (
                    'svc_local_runtime', 'customer_summary', 'RUNTIME_EXECUTE',
                    'SENSITIVE_DETECTION_CONTRACT', 'customer', 'customer-100'
                ) on conflict (principal_id, workload_id, action_name, purpose, subject_type, subject_id) do nothing
                """).update();

        mockMvc.perform(post("/api/runtime/context/preview")
                .header("X-Request-Id", "req_context_sensitive_contract")
                .header("X-Trace-Id", "trace_context_sensitive_contract")
                .header("X-ADP-API-Key", "local-dev-api-key")
                .contentType("application/json")
                .content("""
                    {
                      "workloadId": "customer_summary",
                      "purpose": "SENSITIVE_DETECTION_CONTRACT",
                      "subject": "customer:customer-100"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.schemaVersion").value("canonical-context/v1"))
            .andExpect(jsonPath("$.fields[*].fieldName", hasItem("phone_number")))
            .andExpect(jsonPath("$.fields[*].fieldName", hasItem("email")))
            .andExpect(jsonPath("$.fields[*].fieldName", hasItem("resident_registration_number")))
            .andExpect(jsonPath("$.fields[*].fieldName", hasItem("account_number")))
            .andExpect(jsonPath("$.fields[*].value").doesNotExist())
            .andExpect(jsonPath("$.detection.findings[*].type", hasItem("PHONE_NUMBER")))
            .andExpect(jsonPath("$.detection.findings[*].type", hasItem("EMAIL")))
            .andExpect(jsonPath("$.detection.findings[*].type", hasItem("RESIDENT_REGISTRATION_NUMBER")))
            .andExpect(jsonPath("$.detection.findings[*].type", hasItem("ACCOUNT_NUMBER")));
    }
}
