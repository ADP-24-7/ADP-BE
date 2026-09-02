package com.adp.gateway.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.simple.JdbcClient;

@SpringBootTest
class FlywayMigrationTests {

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private Environment environment;

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
    void migrationCreatesTransformAndVaultTables() {
        Integer tableCount = jdbcClient.sql("""
                select count(*)
                from information_schema.tables
                where (table_schema, table_name) in (
                  ('runtime', 'transform_execution'),
                  ('runtime', 'transform_field'),
                  ('vault', 'token_mapping')
                )
                """)
            .query(Integer.class)
            .single();

        assertThat(tableCount).isEqualTo(3);
    }

    @Test
    void migrationCreatesRuntimeExecutionTransformColumns() {
        Integer columnCount = jdbcClient.sql("""
                select count(*)
                from information_schema.columns
                where table_schema = 'runtime'
                  and table_name = 'runtime_execution'
                  and column_name in (
                    'transform_execution_id',
                    'transform_status',
                    'transform_output_digest'
                  )
                """)
            .query(Integer.class)
            .single();

        assertThat(columnCount).isEqualTo(3);
    }

    @Test
    void migrationCreatesCommonEgressTables() {
        Integer tableCount = jdbcClient.sql("""
                select count(*)
                from information_schema.tables
                where (table_schema, table_name) in (
                  ('egress', 'destination_profile'),
                  ('runtime', 'outbound_candidate'),
                  ('runtime', 'connector_execution'),
                  ('runtime', 'response_guard_result')
                )
                """)
            .query(Integer.class)
            .single();

        assertThat(tableCount).isEqualTo(4);
    }

    @Test
    void migrationCreatesRuntimeExecutionEgressColumns() {
        Integer columnCount = jdbcClient.sql("""
                select count(*)
                from information_schema.columns
                where table_schema = 'runtime'
                  and table_name = 'runtime_execution'
                  and column_name in (
                    'outbound_payload_id',
                    'outbound_candidate_digest',
                    'outbound_guard_status',
                    'connector_execution_id',
                    'connector_status',
                    'response_guard_status',
                    'destination_profile_id',
                    'destination_profile_version',
                    'destination_profile_digest'
                  )
                """)
            .query(Integer.class)
            .single();

        assertThat(columnCount).isEqualTo(9);
    }

    @Test
    void migrationCreatesConnectorExternalStatusConstraints() {
        Integer constraintCount = jdbcClient.sql("""
                select count(*)
                from information_schema.table_constraints
                where constraint_schema = 'runtime'
                  and constraint_name in (
                    'chk_connector_execution_status',
                    'chk_response_guard_connector_status',
                    'chk_runtime_execution_connector_status'
                  )
                """)
            .query(Integer.class)
            .single();

        assertThat(constraintCount).isEqualTo(3);
    }

    @Test
    void v9MigrationCreatesAiPolicyHarnessEvidenceTablesAndColumns() {
        Integer tableCount = jdbcClient.sql("""
                select count(*)
                from information_schema.tables
                where (table_schema, table_name) in (
                  ('runtime', 'policy_harness_binding'),
                  ('runtime', 'provider_request'),
                  ('runtime', 'response_sensitive_finding')
                )
                """)
            .query(Integer.class)
            .single();
        Integer evidenceColumnCount = jdbcClient.sql("""
                select count(*)
                from information_schema.columns
                where table_schema = 'runtime'
                  and table_name = 'runtime_execution'
                  and column_name in (
                    'institution_id',
                    'approval_reference',
                    'approval_reuse_status',
                    'policy_layers_digest',
                    'requested_fields_digest',
                    'released_fields_digest',
                    'provider_request_digest',
                    'provider_response_digest'
                  )
                """)
            .query(Integer.class)
            .single();

        assertThat(tableCount).isEqualTo(3);
        assertThat(evidenceColumnCount).isEqualTo(8);
    }

    @Test
    void v10MigrationCreatesPrincipalInstitutionAndAuthorizationEvidence() {
        Integer principalColumnCount = jdbcClient.sql("""
                select count(*) from information_schema.columns
                where table_schema = 'public'
                  and table_name = 'auth_principal'
                  and column_name = 'institution_id'
                """)
            .query(Integer.class)
            .single();
        Integer authorizationColumnCount = jdbcClient.sql("""
                select count(*) from information_schema.columns
                where table_schema = 'runtime'
                  and table_name = 'runtime_execution'
                  and column_name = 'authorization_status'
                """)
            .query(Integer.class)
            .single();

        assertThat(principalColumnCount).isEqualTo(1);
        assertThat(authorizationColumnCount).isEqualTo(1);
    }

    @Test
    void v8MigrationPreservesLegacyAuditConnectorStatusRows() throws Exception {
        String databaseName = "adp_upgrade_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String sourceUrl = environment.getRequiredProperty("spring.datasource.url");
        String username = environment.getRequiredProperty("spring.datasource.username");
        String password = environment.getRequiredProperty("spring.datasource.password");
        String upgradeUrl = databaseUrl(sourceUrl, databaseName);
        createDatabase(sourceUrl, username, password, databaseName);
        try {
            Flyway.configure()
                .dataSource(upgradeUrl, username, password)
                .locations("classpath:db/migration")
                .target("7")
                .load()
                .migrate();
            insertLegacyAuditRows(upgradeUrl, username, password);

            Flyway.configure()
                .dataSource(upgradeUrl, username, password)
                .locations("classpath:db/migration")
                .load()
                .migrate();

            try (var connection = DriverManager.getConnection(upgradeUrl, username, password);
                 var statement = connection.createStatement();
                 var resultSet = statement.executeQuery("""
                     select count(*)
                     from audit_event
                     where connector_status in ('EXECUTED', 'NOT_EXECUTED')
                     """)) {
                resultSet.next();
                assertThat(resultSet.getInt(1)).isEqualTo(2);
            }
        } finally {
            dropDatabase(sourceUrl, username, password, databaseName);
        }
    }

    @Test
    void migrationCreatesTransformInstructionMetadataColumns() {
        Integer columnCount = jdbcClient.sql("""
                select count(*)
                from information_schema.columns
                where table_schema = 'runtime'
                  and table_name = 'transform_field'
                  and column_name in (
                    'strategy_version',
                    'key_version',
                    'mapping_version',
                    'instruction_digest'
                  )
                """)
            .query(Integer.class)
            .single();

        assertThat(columnCount).isEqualTo(4);
    }

    @Test
    void migrationCreatesVaultLifecycleColumns() {
        Integer columnCount = jdbcClient.sql("""
                select count(*)
                from information_schema.columns
                where table_schema = 'vault'
                  and table_name = 'token_mapping'
                  and column_name in (
                    'status',
                    'replaced_by_token_ref'
                  )
                """)
            .query(Integer.class)
            .single();

        assertThat(columnCount).isEqualTo(2);
    }

    @Test
    void migrationCreatesRuntimeExecutionIdempotencyConstraint() {
        Integer indexCount = jdbcClient.sql("""
                select count(*)
                from pg_indexes
                where schemaname = 'runtime'
                  and tablename = 'runtime_execution'
                  and indexname = 'uq_runtime_execution_workload_idempotency'
                """)
            .query(Integer.class)
            .single();

        assertThat(indexCount).isEqualTo(1);
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

    private void createDatabase(String sourceUrl, String username, String password, String databaseName)
        throws SQLException {
        try (var connection = DriverManager.getConnection(databaseUrl(sourceUrl, "postgres"), username, password);
             var statement = connection.createStatement()) {
            statement.execute("create database " + databaseName);
        }
    }

    private void dropDatabase(String sourceUrl, String username, String password, String databaseName)
        throws SQLException {
        try (var connection = DriverManager.getConnection(databaseUrl(sourceUrl, "postgres"), username, password);
             var statement = connection.createStatement()) {
            statement.execute("""
                select pg_terminate_backend(pid)
                from pg_stat_activity
                where datname = '%s'
                """.formatted(databaseName));
            statement.execute("drop database if exists " + databaseName);
        }
    }

    private void insertLegacyAuditRows(String url, String username, String password) throws SQLException {
        try (var connection = DriverManager.getConnection(url, username, password);
             var statement = connection.createStatement()) {
            statement.execute("""
                insert into audit_event (
                    audit_id, request_id, trace_id, idempotency_key, workload_id,
                    decision_id, reason_code, connector_status, created_at,
                    policy_artifact_id, policy_version, policy_digest,
                    policy_action, policy_artifact_version, policy_artifact_digest_algorithm,
                    policy_artifact_digest_value, final_action, authorization_result,
                    applicability_result, runtime_context_digest, matched_rule_ids,
                    evidence_refs, required_controls
                )
                values
                    (
                        'aud_legacy_executed', 'req_legacy_executed', 'trace_legacy_executed',
                        'idem_legacy_executed', 'workload_legacy', 'decision_legacy_executed',
                        'ALLOW', 'EXECUTED', now(), 'PROJECT_PROVISIONAL_POLICY_EVALUATION',
                        'be-runtime-policy/0.0.0', 'legacy-policy-digest', 'ALLOW',
                        '0.0.0', 'sha256', 'legacy-artifact-digest', 'ALLOW',
                        'ALLOWED', 'APPLICABLE', 'legacy-context-digest', '', '', ''
                    ),
                    (
                        'aud_legacy_not_executed', 'req_legacy_not_executed', 'trace_legacy_not_executed',
                        'idem_legacy_not_executed', 'workload_legacy', 'decision_legacy_not_executed',
                        'REVIEW', 'NOT_EXECUTED', now(), 'PROJECT_PROVISIONAL_POLICY_EVALUATION',
                        'be-runtime-policy/0.0.0', 'legacy-policy-digest', 'ALLOW',
                        '0.0.0', 'sha256', 'legacy-artifact-digest', 'REVIEW',
                        'ALLOWED', 'INCOMPLETE', 'legacy-context-digest', '', '', ''
                    )
                """);
        }
    }

    private String databaseUrl(String sourceUrl, String databaseName) {
        int queryIndex = sourceUrl.indexOf('?');
        String query = queryIndex >= 0 ? sourceUrl.substring(queryIndex) : "";
        String base = queryIndex >= 0 ? sourceUrl.substring(0, queryIndex) : sourceUrl;
        int databaseSeparator = base.lastIndexOf('/');
        return base.substring(0, databaseSeparator + 1) + databaseName + query;
    }
}
