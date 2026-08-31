package com.adp.gateway.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

@SpringBootTest
class FlywayMigrationTests {

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void baselineMigrationCreatesAuditEventTable() {
        Integer tableCount = jdbcClient.sql("""
                select count(*)
                from information_schema.tables
                where table_schema = 'public'
                  and table_name = 'audit_event'
                """)
            .query(Integer.class)
            .single();

        assertThat(tableCount).isEqualTo(1);
    }

    @Test
    void migrationCreatesPolicySnapshotColumns() {
        Integer columnCount = jdbcClient.sql("""
                select count(*)
                from information_schema.columns
                where table_schema = 'public'
                  and table_name = 'audit_event'
                  and column_name in ('policy_artifact_id', 'policy_version', 'policy_digest')
                """)
            .query(Integer.class)
            .single();

        assertThat(columnCount).isEqualTo(3);
    }

    @Test
    void migrationCreatesRuntimeDecisionAuditColumns() {
        Integer columnCount = jdbcClient.sql("""
                select count(*)
                from information_schema.columns
                where table_schema = 'public'
                  and table_name = 'audit_event'
                  and column_name in (
                    'policy_action',
                    'policy_artifact_version',
                    'policy_artifact_digest_algorithm',
                    'policy_artifact_digest_value',
                    'final_action',
                    'authorization_result',
                    'applicability_result',
                    'runtime_context_digest',
                    'matched_rule_ids',
                    'evidence_refs',
                    'required_controls'
                  )
                """)
            .query(Integer.class)
            .single();

        assertThat(columnCount).isEqualTo(11);
    }

    @Test
    void migrationCreatesRuntimeExecutionPolicyDecisionTables() {
        Integer tableCount = jdbcClient.sql("""
                select count(*)
                from information_schema.tables
                where (table_schema, table_name) in (
                  ('runtime', 'runtime_execution'),
                  ('governance', 'policy_snapshot'),
                  ('runtime', 'policy_evaluation'),
                  ('runtime', 'runtime_decision')
                )
                """)
            .query(Integer.class)
            .single();

        assertThat(tableCount).isEqualTo(4);
    }

    @Test
    void migrationCreatesAuthBaselineTables() {
        Integer tableCount = jdbcClient.sql("""
                select count(*)
                from information_schema.tables
                where table_schema = 'public'
                  and table_name in (
                    'auth_principal',
                    'auth_principal_role',
                    'auth_api_key',
                    'auth_principal_workload',
                    'auth_subject_grant'
                  )
                """)
            .query(Integer.class)
            .single();

        assertThat(tableCount).isEqualTo(5);
    }

    @Test
    void migrationCreatesDataAccessBaselineTables() {
        Integer tableCount = jdbcClient.sql("""
                select count(*)
                from information_schema.tables
                where table_schema = 'public'
                  and table_name in (
                    'workload_registry',
                    'retrieval_profile',
                    'retrieval_profile_dataset',
                    'retrieval_profile_field',
                    'synthetic_customer',
                    'synthetic_account',
                    'synthetic_transaction',
                    'data_access_event'
                  )
                """)
            .query(Integer.class)
            .single();

        assertThat(tableCount).isEqualTo(8);
    }
}
