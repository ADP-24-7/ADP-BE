package com.adp.gateway.recovery.infrastructure;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.adp.gateway.connector.domain.ConnectorStatus;
import com.adp.gateway.recovery.application.RecoveryOperationException;
import com.adp.gateway.recovery.application.RecoveryOperationsPersistence;
import com.adp.gateway.recovery.domain.RecoveryIncidentDetail;
import com.adp.gateway.recovery.domain.RecoveryIncidentPage;
import com.adp.gateway.recovery.domain.RecoveryIncidentSummary;
import com.adp.gateway.recovery.domain.RecoveryOperationEvent;
import com.adp.gateway.recovery.domain.RecoveryOperationOutcome;
import com.adp.gateway.recovery.domain.RecoveryOperationType;
import com.adp.gateway.recovery.domain.RecoveryStatus;
import com.adp.gateway.recovery.domain.RetryDisposition;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class JdbcRecoveryOperationsPersistence implements RecoveryOperationsPersistence {

    private static final RowMapper<RecoveryOperationEvent> OPERATION_MAPPER = (rs, rowNum) ->
        new RecoveryOperationEvent(
            rs.getString("operation_id"),
            rs.getString("actor_principal_id"),
            RecoveryOperationType.valueOf(rs.getString("operation_type")),
            RecoveryOperationOutcome.valueOf(rs.getString("outcome")),
            rs.getString("reason_code"),
            rs.getString("evidence_digest"),
            rs.getObject("created_at", OffsetDateTime.class),
            rs.getObject("completed_at", OffsetDateTime.class)
        );

    private final JdbcClient jdbcClient;

    public JdbcRecoveryOperationsPersistence(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public RecoveryIncidentPage search(
        String institutionId,
        Set<String> allowedWorkloads,
        RecoveryStatus status,
        int page,
        int size
    ) {
        StringBuilder where = new StringBuilder(" where re.institution_id = :institutionId");
        appendWorkloadScope(where, allowedWorkloads);
        if (status != null) {
            where.append(" and r.recovery_status = :status");
        }
        String from = """
            from runtime.external_interaction_recovery r
            join runtime.runtime_execution re on re.execution_id = r.execution_id
            """;
        JdbcClient.StatementSpec select = bindScope(jdbcClient.sql("""
            select r.recovery_id, r.execution_id, re.institution_id, re.workload_id, re.purpose_code,
                   r.connector_id, r.observed_status, r.last_observed_external_status,
                   r.recovery_status, r.retry_disposition, r.attempt_count, r.max_attempts,
                   r.next_attempt_at, r.last_error_code, r.created_at, r.updated_at
            """ + from + where + " order by r.updated_at desc, r.recovery_id desc limit :size offset :offset"),
            institutionId, allowedWorkloads)
            .param("size", size)
            .param("offset", page * size);
        JdbcClient.StatementSpec count = bindScope(
            jdbcClient.sql("select count(*) " + from + where), institutionId, allowedWorkloads
        );
        if (status != null) {
            select = select.param("status", status.name());
            count = count.param("status", status.name());
        }
        List<RecoveryIncidentSummary> items = select.query((rs, rowNum) -> new RecoveryIncidentSummary(
            rs.getString("recovery_id"), rs.getString("execution_id"), rs.getString("institution_id"),
            rs.getString("workload_id"), rs.getString("purpose_code"), rs.getString("connector_id"),
            ConnectorStatus.valueOf(rs.getString("observed_status")),
            nullableConnectorStatus(rs.getString("last_observed_external_status")),
            RecoveryStatus.valueOf(rs.getString("recovery_status")),
            RetryDisposition.valueOf(rs.getString("retry_disposition")),
            rs.getInt("attempt_count"), rs.getInt("max_attempts"),
            rs.getObject("next_attempt_at", OffsetDateTime.class), rs.getString("last_error_code"),
            rs.getObject("created_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class)
        )).list();
        return new RecoveryIncidentPage(items, page, size, count.query(Long.class).single());
    }

    @Override
    public RecoveryIncidentDetail load(
        String recoveryId,
        String institutionId,
        Set<String> allowedWorkloads
    ) {
        StringBuilder scope = new StringBuilder(" and re.institution_id = :institutionId");
        appendWorkloadScope(scope, allowedWorkloads);
        JdbcClient.StatementSpec statement = bindScope(jdbcClient.sql("""
            select r.recovery_id, r.execution_id, re.institution_id, re.workload_id, re.purpose_code,
                   r.connector_execution_id, r.connector_id, r.observed_status,
                   r.last_observed_external_status, r.recovery_status, r.retry_disposition,
                   r.attempt_count, r.max_attempts, r.next_attempt_at, r.lease_until,
                   r.last_error_code, r.last_status_queried_at, r.status_query_evidence_digest,
                   r.created_at, r.updated_at
            from runtime.external_interaction_recovery r
            join runtime.runtime_execution re on re.execution_id = r.execution_id
            where r.recovery_id = :recoveryId
            """ + scope), institutionId, allowedWorkloads).param("recoveryId", recoveryId);
        RecoveryIncidentRow row = statement.query((rs, rowNum) -> new RecoveryIncidentRow(
            rs.getString("recovery_id"), rs.getString("execution_id"), rs.getString("institution_id"),
            rs.getString("workload_id"), rs.getString("purpose_code"),
            rs.getString("connector_execution_id"), rs.getString("connector_id"),
            ConnectorStatus.valueOf(rs.getString("observed_status")),
            nullableConnectorStatus(rs.getString("last_observed_external_status")),
            RecoveryStatus.valueOf(rs.getString("recovery_status")),
            RetryDisposition.valueOf(rs.getString("retry_disposition")),
            rs.getInt("attempt_count"), rs.getInt("max_attempts"),
            rs.getObject("next_attempt_at", OffsetDateTime.class),
            rs.getObject("lease_until", OffsetDateTime.class), rs.getString("last_error_code"),
            rs.getObject("last_status_queried_at", OffsetDateTime.class),
            rs.getString("status_query_evidence_digest"),
            rs.getObject("created_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class)
        )).optional().orElseThrow(() -> new RecoveryOperationException("RECOVERY_INCIDENT_NOT_FOUND"));
        return row.toDetail(loadOperations(recoveryId));
    }

    @Override
    public Optional<RecoveryOperationEvent> findOperation(
        String recoveryId,
        String operationId,
        String institutionId,
        Set<String> allowedWorkloads
    ) {
        StringBuilder scope = new StringBuilder(" and e.institution_id = :institutionId");
        appendOperationWorkloadScope(scope, allowedWorkloads);
        return bindScope(jdbcClient.sql("""
            select e.operation_id, e.actor_principal_id, e.operation_type, e.outcome,
                   e.reason_code, e.evidence_digest, e.created_at, e.completed_at
            from runtime.recovery_operation_event e
            where e.recovery_id = :recoveryId and e.operation_id = :operationId
            """ + scope), institutionId, allowedWorkloads)
            .param("recoveryId", recoveryId)
            .param("operationId", operationId)
            .query(OPERATION_MAPPER)
            .optional();
    }

    @Override
    public boolean reserveOperation(
        String recoveryId,
        String operationId,
        String institutionId,
        Set<String> allowedWorkloads,
        String actorPrincipalId,
        RecoveryOperationType operationType,
        OffsetDateTime now
    ) {
        String workloadScope = allowedWorkloads.contains("*")
            ? ""
            : allowedWorkloads.isEmpty() ? " and 1 = 0" : " and re.workload_id in (:allowedWorkloads)";
        JdbcClient.StatementSpec statement = jdbcClient.sql("""
            insert into runtime.recovery_operation_event (
                event_id, operation_id, recovery_id, execution_id, institution_id, workload_id,
                actor_principal_id, operation_type, outcome, created_at
            )
            select :eventId, :operationId, r.recovery_id, r.execution_id, re.institution_id, re.workload_id,
                   :actorPrincipalId, :operationType, 'IN_PROGRESS', :createdAt
            from runtime.external_interaction_recovery r
            join runtime.runtime_execution re on re.execution_id = r.execution_id
            where r.recovery_id = :recoveryId and re.institution_id = :institutionId
            """ + workloadScope + " on conflict (recovery_id, operation_id) do nothing")
            .param("eventId", "rope_" + java.util.UUID.randomUUID())
            .param("operationId", operationId)
            .param("recoveryId", recoveryId)
            .param("institutionId", institutionId)
            .param("actorPrincipalId", actorPrincipalId)
            .param("operationType", operationType.name())
            .param("createdAt", now);
        if (!allowedWorkloads.contains("*") && !allowedWorkloads.isEmpty()) {
            statement = statement.param("allowedWorkloads", allowedWorkloads);
        }
        return statement.update() == 1;
    }

    @Override
    public void completeOperation(
        String recoveryId,
        String operationId,
        RecoveryOperationOutcome outcome,
        String reasonCode,
        String evidenceDigest,
        OffsetDateTime now
    ) {
        int updated = jdbcClient.sql("""
            update runtime.recovery_operation_event
            set outcome = :outcome, reason_code = :reasonCode,
                evidence_digest = :evidenceDigest, completed_at = :completedAt
            where recovery_id = :recoveryId and operation_id = :operationId
              and outcome = 'IN_PROGRESS'
            """)
            .param("recoveryId", recoveryId)
            .param("operationId", operationId)
            .param("outcome", outcome.name())
            .param("reasonCode", reasonCode)
            .param("evidenceDigest", evidenceDigest)
            .param("completedAt", now)
            .update();
        if (updated != 1) {
            throw new RecoveryOperationException("RECOVERY_COMMAND_CONFLICT");
        }
    }

    private List<RecoveryOperationEvent> loadOperations(String recoveryId) {
        return jdbcClient.sql("""
            select operation_id, actor_principal_id, operation_type, outcome,
                   reason_code, evidence_digest, created_at, completed_at
            from runtime.recovery_operation_event
            where recovery_id = :recoveryId
            order by created_at desc, event_id desc
            """)
            .param("recoveryId", recoveryId)
            .query(OPERATION_MAPPER)
            .list();
    }

    private JdbcClient.StatementSpec bindScope(
        JdbcClient.StatementSpec statement,
        String institutionId,
        Set<String> allowedWorkloads
    ) {
        statement = statement.param("institutionId", institutionId);
        if (!allowedWorkloads.contains("*") && !allowedWorkloads.isEmpty()) {
            statement = statement.param("allowedWorkloads", allowedWorkloads);
        }
        return statement;
    }

    private void appendWorkloadScope(StringBuilder sql, Set<String> allowedWorkloads) {
        if (allowedWorkloads.contains("*")) {
            return;
        }
        sql.append(allowedWorkloads.isEmpty() ? " and 1 = 0" : " and re.workload_id in (:allowedWorkloads)");
    }

    private void appendOperationWorkloadScope(StringBuilder sql, Set<String> allowedWorkloads) {
        if (allowedWorkloads.contains("*")) {
            return;
        }
        sql.append(allowedWorkloads.isEmpty() ? " and 1 = 0" : " and e.workload_id in (:allowedWorkloads)");
    }

    private static ConnectorStatus nullableConnectorStatus(String value) {
        return value == null ? null : ConnectorStatus.valueOf(value);
    }

    private record RecoveryIncidentRow(
        String recoveryId,
        String executionId,
        String institutionId,
        String workloadId,
        String purposeCode,
        String connectorExecutionId,
        String connectorId,
        ConnectorStatus observedStatus,
        ConnectorStatus lastObservedExternalStatus,
        RecoveryStatus recoveryStatus,
        RetryDisposition retryDisposition,
        int attemptCount,
        int maxAttempts,
        OffsetDateTime nextAttemptAt,
        OffsetDateTime leaseUntil,
        String lastErrorCode,
        OffsetDateTime lastStatusQueriedAt,
        String statusQueryEvidenceDigest,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
    ) {
        RecoveryIncidentDetail toDetail(List<RecoveryOperationEvent> operations) {
            return new RecoveryIncidentDetail(
                recoveryId, executionId, institutionId, workloadId, purposeCode,
                connectorExecutionId, connectorId, observedStatus, lastObservedExternalStatus,
                recoveryStatus, retryDisposition, attemptCount, maxAttempts, nextAttemptAt,
                leaseUntil, lastErrorCode, lastStatusQueriedAt, statusQueryEvidenceDigest,
                createdAt, updatedAt, operations
            );
        }
    }
}
