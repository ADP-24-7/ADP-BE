package com.adp.gateway.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

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
    void v11MigrationCreatesControlledDeliveryEvidence() {
        Integer columnCount = jdbcClient.sql("""
                select count(*) from information_schema.columns
                where table_schema = 'runtime'
                  and table_name = 'runtime_execution'
                  and column_name in (
                    'controlled_delivery_status',
                    'controlled_delivery_response_digest',
                    'controlled_delivery_reason_code',
                    'controlled_delivered_at'
                  )
                """)
            .query(Integer.class)
            .single();
        Integer constraintCount = jdbcClient.sql("""
                select count(*) from information_schema.table_constraints
                where constraint_schema = 'runtime'
                  and constraint_name = 'chk_runtime_execution_controlled_delivery_status'
                """)
            .query(Integer.class)
            .single();

        assertThat(columnCount).isEqualTo(4);
        assertThat(constraintCount).isEqualTo(1);
    }

    @Test
    void v13MigrationCreatesExternalInteractionRecoveryQueue() {
        Integer tableCount = jdbcClient.sql("""
                select count(*) from information_schema.tables
                where table_schema = 'runtime'
                  and table_name = 'external_interaction_recovery'
                """)
            .query(Integer.class)
            .single();
        Integer constraintCount = jdbcClient.sql("""
                select count(*) from information_schema.table_constraints
                where constraint_schema = 'runtime'
                  and table_name = 'external_interaction_recovery'
                  and constraint_type in ('CHECK', 'UNIQUE')
                """)
            .query(Integer.class)
            .single();

        assertThat(tableCount).isEqualTo(1);
        assertThat(constraintCount).isGreaterThanOrEqualTo(5);
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
                  and indexname = 'uq_runtime_execution_idempotency_scope'
                """)
            .query(Integer.class)
            .single();

        assertThat(indexCount).isEqualTo(1);

        Integer columnCount = jdbcClient.sql("""
                select count(*)
                from information_schema.columns
                where table_schema = 'runtime'
                  and table_name = 'runtime_execution'
                  and column_name in ('idempotency_institution_id', 'request_hash')
                  and is_nullable = 'NO'
                """)
            .query(Integer.class)
            .single();
        assertThat(columnCount).isEqualTo(2);

        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        assertThatCode(() -> jdbcClient.sql("""
                insert into runtime.runtime_execution (
                    execution_id, request_id, trace_id, idempotency_key, workload_id,
                    idempotency_institution_id, request_hash, purpose_code, input_digest,
                    status, created_at, updated_at
                ) values
                    (:executionA, :requestA, :traceA, :key, 'namespace_test',
                     'institution_a', :hashA, 'TEST', :inputA, 'RECEIVED', now(), now()),
                    (:executionB, :requestB, :traceB, :key, 'namespace_test',
                     'institution_b', :hashB, 'TEST', :inputB, 'RECEIVED', now(), now())
                """)
            .param("executionA", "exec_ns_a_" + suffix)
            .param("requestA", "req_ns_a_" + suffix)
            .param("traceA", "trace_ns_a_" + suffix)
            .param("executionB", "exec_ns_b_" + suffix)
            .param("requestB", "req_ns_b_" + suffix)
            .param("traceB", "trace_ns_b_" + suffix)
            .param("key", "idem_ns_" + suffix)
            .param("hashA", "a".repeat(64))
            .param("hashB", "b".repeat(64))
            .param("inputA", "c".repeat(64))
            .param("inputB", "d".repeat(64))
            .update()).doesNotThrowAnyException();
    }

    @Test
    void v12MigrationBackfillsLegacyRuntimeExecutions() throws Exception {
        String databaseName = "adp_v12_upgrade_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String sourceUrl = environment.getRequiredProperty("spring.datasource.url");
        String username = environment.getRequiredProperty("spring.datasource.username");
        String password = environment.getRequiredProperty("spring.datasource.password");
        String upgradeUrl = databaseUrl(sourceUrl, databaseName);
        createDatabase(sourceUrl, username, password, databaseName);
        try {
            Flyway.configure()
                .dataSource(upgradeUrl, username, password)
                .locations("classpath:db/migration")
                .target("11")
                .load()
                .migrate();
            try (var connection = DriverManager.getConnection(upgradeUrl, username, password);
                 var statement = connection.createStatement()) {
                statement.execute("""
                    insert into runtime.runtime_execution (
                        execution_id, request_id, trace_id, idempotency_key, workload_id,
                        purpose_code, input_digest, status, created_at, updated_at
                    ) values (
                        'exec_legacy_v11', 'req_legacy_v11', 'trace_legacy_v11', 'idem_legacy_v11',
                        'customer_summary', 'CUSTOMER_SUPPORT', repeat('a', 64), 'COMPLETED', now(), now()
                    )
                    """);
            }

            Flyway.configure()
                .dataSource(upgradeUrl, username, password)
                .locations("classpath:db/migration")
                .load()
                .migrate();

            try (var connection = DriverManager.getConnection(upgradeUrl, username, password);
                 var statement = connection.createStatement();
                 var resultSet = statement.executeQuery("""
                     select idempotency_institution_id, request_hash
                     from runtime.runtime_execution
                     where execution_id = 'exec_legacy_v11'
                     """)) {
                resultSet.next();
                assertThat(resultSet.getString("idempotency_institution_id")).isEqualTo("LEGACY_UNSCOPED");
                assertThat(resultSet.getString("request_hash")).hasSize(64);
            }
        } finally {
            dropDatabase(sourceUrl, username, password, databaseName);
        }
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

    @Test
    void v14MigrationCreatesAuditReadModelIndexes() {
        Integer indexCount = jdbcClient.sql("""
                select count(*)
                from pg_indexes
                where schemaname = 'runtime'
                  and indexname in (
                    'idx_runtime_execution_audit_search',
                    'idx_runtime_execution_audit_status_search',
                    'idx_runtime_execution_audit_workload_search'
                  )
                """)
            .query(Integer.class)
            .single();

        assertThat(indexCount).isEqualTo(3);
    }

    @Test
    void v15MigrationBindsAuditEventsToRuntimeExecutions() {
        Integer columnCount = jdbcClient.sql("""
                select count(*)
                from information_schema.columns
                where table_schema = 'public'
                  and table_name = 'audit_event'
                  and column_name = 'execution_id'
                """)
            .query(Integer.class)
            .single();
        Integer constraintCount = jdbcClient.sql("""
                select count(*)
                from information_schema.table_constraints
                where table_schema = 'public'
                  and table_name = 'audit_event'
                  and constraint_name = 'fk_audit_event_runtime_execution'
                  and constraint_type = 'FOREIGN KEY'
                """)
            .query(Integer.class)
            .single();

        assertThat(columnCount).isEqualTo(1);
        assertThat(constraintCount).isEqualTo(1);
    }

    @Test
    void v16MigrationCreatesDigitalAssetEvidenceTable() {
        Integer tableCount = jdbcClient.sql("""
                select count(*) from information_schema.tables
                where table_schema = 'runtime' and table_name = 'digital_asset_transaction'
                """)
            .query(Integer.class).single();
        Integer constraintCount = jdbcClient.sql("""
                select count(*) from information_schema.table_constraints
                where table_schema = 'runtime'
                  and table_name = 'digital_asset_transaction'
                  and constraint_name in (
                    'digital_asset_transaction_execution_id_fkey',
                    'chk_digital_asset_settlement_status',
                    'chk_digital_asset_reconciliation',
                    'chk_digital_asset_settled_evidence'
                  )
                """)
            .query(Integer.class).single();

        assertThat(tableCount).isEqualTo(1);
        assertThat(constraintCount).isEqualTo(4);
        Integer combinationConstraint = jdbcClient.sql("""
                select count(*) from information_schema.table_constraints
                where table_schema = 'runtime'
                  and table_name = 'digital_asset_transaction'
                  and constraint_name = 'chk_digital_asset_state_reconciliation_combination'
                """)
            .query(Integer.class).single();
        assertThat(combinationConstraint).isEqualTo(1);
    }

    @Test
    void v15MigrationBackfillsExistingAuditEventExecutionId() throws Exception {
        String databaseName = "adp_v15_upgrade_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String sourceUrl = environment.getRequiredProperty("spring.datasource.url");
        String username = environment.getRequiredProperty("spring.datasource.username");
        String password = environment.getRequiredProperty("spring.datasource.password");
        String upgradeUrl = databaseUrl(sourceUrl, databaseName);
        createDatabase(sourceUrl, username, password, databaseName);
        try {
            Flyway.configure()
                .dataSource(upgradeUrl, username, password)
                .locations("classpath:db/migration")
                .target("14")
                .load()
                .migrate();
            try (var connection = DriverManager.getConnection(upgradeUrl, username, password);
                 var statement = connection.createStatement()) {
                statement.execute("""
                    insert into runtime.runtime_execution (
                        execution_id, request_id, trace_id, idempotency_key, workload_id,
                        purpose_code, input_digest, decision_id, status,
                        idempotency_institution_id, request_hash, created_at, updated_at
                    ) values (
                        'exec_v14_audit', 'req_v14_audit', 'trace_v14_audit', 'idem_v14_audit',
                        'customer_summary', 'CUSTOMER_SUPPORT', repeat('a', 64), 'decision_v14_audit',
                        'COMPLETED', 'institution_local', repeat('b', 64), now(), now()
                    )
                    """);
                statement.execute("""
                    insert into audit_event (
                        audit_id, request_id, trace_id, idempotency_key, workload_id,
                        decision_id, reason_code, connector_status, created_at,
                        policy_artifact_id, policy_version, policy_digest,
                        policy_action, policy_artifact_version, policy_artifact_digest_algorithm,
                        policy_artifact_digest_value, final_action, authorization_result,
                        applicability_result, runtime_context_digest, matched_rule_ids,
                        evidence_refs, required_controls
                    ) values (
                        'aud_v14_audit', 'req_v14_audit', 'trace_v14_audit', 'idem_v14_audit',
                        'customer_summary', 'decision_v14_audit', 'ALLOW', 'COMPLETED', now(),
                        'artifact_v14', 'policy_v14', 'digest_v14', 'ALLOW', 'artifact_version_v14',
                        'sha256', 'artifact_digest_v14', 'ALLOW', 'ALLOWED', 'APPLICABLE',
                        repeat('c', 64), '', '', ''
                    )
                    """);
            }

            Flyway.configure()
                .dataSource(upgradeUrl, username, password)
                .locations("classpath:db/migration")
                .load()
                .migrate();

            try (var connection = DriverManager.getConnection(upgradeUrl, username, password);
                 var statement = connection.createStatement();
                 var resultSet = statement.executeQuery("""
                     select execution_id from audit_event where audit_id = 'aud_v14_audit'
                     """)) {
                resultSet.next();
                assertThat(resultSet.getString("execution_id")).isEqualTo("exec_v14_audit");
            }
        } finally {
            dropDatabase(sourceUrl, username, password, databaseName);
        }
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
