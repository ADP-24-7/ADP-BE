package com.adp.gateway.recovery.infrastructure;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;

import com.adp.gateway.connector.domain.ConnectorResult;
import com.adp.gateway.connector.domain.ConnectorStatus;
import com.adp.gateway.recovery.application.ExternalInteractionRecoveryPersistence;
import com.adp.gateway.recovery.domain.ExternalInteractionRecovery;
import com.adp.gateway.recovery.domain.RecoveryStatus;
import com.adp.gateway.recovery.domain.RetryDisposition;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JdbcExternalInteractionRecoveryPersistence implements ExternalInteractionRecoveryPersistence {

    private final JdbcClient jdbcClient;
    private final java.time.Clock clock;

    public JdbcExternalInteractionRecoveryPersistence(JdbcClient jdbcClient, java.time.Clock clock) {
        this.jdbcClient = jdbcClient;
        this.clock = clock;
    }

    @Override
    public void scheduleUnknown(String executionId, ConnectorResult result, OffsetDateTime now) {
        jdbcClient.sql("""
                insert into runtime.external_interaction_recovery (
                    recovery_id, execution_id, connector_execution_id, connector_id, provider_correlation_key,
                    observed_status, recovery_status, retry_disposition,
                    attempt_count, max_attempts, next_attempt_at, created_at, updated_at
                ) values (
                    :recoveryId, :executionId, :connectorExecutionId, :connectorId,
                    (select provider_request_id from runtime.runtime_execution where execution_id = :executionId),
                    'SENT_UNKNOWN', 'PENDING', 'RECONCILE_FIRST',
                    0, 5, :now, :now, :now
                ) on conflict (execution_id) do nothing
                """)
            .param("recoveryId", "rec_" + java.util.UUID.randomUUID())
            .param("executionId", executionId)
            .param("connectorExecutionId", result.connectorExecutionId())
            .param("connectorId", result.connectorId())
            .param("now", now)
            .update();
    }

    @Override
    @Transactional
    public Optional<ExternalInteractionRecovery> claimNext(
        String workerId,
        OffsetDateTime now,
        Duration leaseDuration
    ) {
        jdbcClient.sql("""
                update runtime.external_interaction_recovery
                set recovery_status = 'EXHAUSTED',
                    lease_owner = null,
                    lease_until = null,
                    last_error_code = 'RECOVERY_ATTEMPTS_EXHAUSTED',
                    updated_at = :now
                where recovery_status = 'CLAIMED'
                  and lease_until <= :now
                  and attempt_count >= max_attempts
                """)
            .param("now", now)
            .update();
        return jdbcClient.sql("""
                with due as (
                    select recovery_id
                    from runtime.external_interaction_recovery
                    where recovery_status in ('PENDING', 'RETRY_SCHEDULED', 'CLAIMED')
                      and next_attempt_at <= :now
                      and (lease_until is null or lease_until <= :now)
                      and attempt_count < max_attempts
                    order by next_attempt_at, created_at
                    for update skip locked
                    limit 1
                )
                update runtime.external_interaction_recovery r
                set recovery_status = 'CLAIMED',
                    lease_owner = :workerId,
                    lease_until = :leaseUntil,
                    attempt_count = attempt_count + 1,
                    updated_at = :now
                from due
                where r.recovery_id = due.recovery_id
                returning r.*
                """)
            .param("workerId", workerId)
            .param("now", now)
            .param("leaseUntil", now.plus(leaseDuration))
            .query((rs, rowNum) -> new ExternalInteractionRecovery(
                rs.getString("recovery_id"),
                rs.getString("execution_id"),
                rs.getString("connector_execution_id"),
                rs.getString("connector_id"),
                rs.getString("provider_correlation_key"),
                ConnectorStatus.valueOf(rs.getString("observed_status")),
                rs.getString("last_observed_external_status") == null
                    ? null : ConnectorStatus.valueOf(rs.getString("last_observed_external_status")),
                RecoveryStatus.valueOf(rs.getString("recovery_status")),
                RetryDisposition.valueOf(rs.getString("retry_disposition")),
                rs.getInt("attempt_count"),
                rs.getInt("max_attempts"),
                rs.getObject("next_attempt_at", OffsetDateTime.class),
                rs.getString("lease_owner"),
                rs.getObject("lease_until", OffsetDateTime.class),
                rs.getString("last_error_code"),
                rs.getObject("last_status_queried_at", OffsetDateTime.class),
                rs.getString("status_query_evidence_digest")
            ))
            .optional();
    }

    @Override
    public boolean reschedule(String recoveryId, String workerId, OffsetDateTime nextAttemptAt, String errorCode) {
        return jdbcClient.sql("""
                update runtime.external_interaction_recovery
                set recovery_status = case when attempt_count >= max_attempts then 'EXHAUSTED' else 'RETRY_SCHEDULED' end,
                    next_attempt_at = :nextAttemptAt,
                    lease_owner = null,
                    lease_until = null,
                    last_error_code = :errorCode,
                    updated_at = :updatedAt
                where recovery_id = :recoveryId
                  and recovery_status = 'CLAIMED'
                  and lease_owner = :workerId
                """)
            .param("recoveryId", recoveryId)
            .param("workerId", workerId)
            .param("nextAttemptAt", nextAttemptAt)
            .param("errorCode", errorCode)
            .param("updatedAt", OffsetDateTime.now(clock))
            .update() == 1;
    }

    @Override
    @Transactional
    public boolean reconcile(
        String recoveryId,
        String workerId,
        com.adp.gateway.recovery.domain.ExternalStatusQueryResult result,
        OffsetDateTime queriedAt
    ) {
        int recoveryUpdated = jdbcClient.sql("""
                update runtime.external_interaction_recovery
                set recovery_status = 'RECONCILED',
                    last_observed_external_status = :externalStatus,
                    last_status_queried_at = :queriedAt,
                    status_query_evidence_digest = :evidenceDigest,
                    lease_owner = null,
                    lease_until = null,
                    last_error_code = null,
                    updated_at = :queriedAt
                where recovery_id = :recoveryId
                  and recovery_status = 'CLAIMED'
                  and lease_owner = :workerId
                """)
            .param("recoveryId", recoveryId)
            .param("workerId", workerId)
            .param("externalStatus", result.status().name())
            .param("queriedAt", queriedAt)
            .param("evidenceDigest", result.evidenceDigest())
            .update();
        if (recoveryUpdated == 0) {
            return false;
        }
        jdbcClient.sql("""
                update runtime.connector_execution ce
                set status = :externalStatus
                from runtime.external_interaction_recovery r
                where r.recovery_id = :recoveryId
                  and ce.connector_execution_id = r.connector_execution_id
                """)
            .param("recoveryId", recoveryId)
            .param("externalStatus", result.status().name())
            .update();
        jdbcClient.sql("""
                update runtime.runtime_execution re
                set connector_status = :externalStatus,
                    status = 'EXTERNALLY_RECONCILED',
                    updated_at = :queriedAt
                from runtime.external_interaction_recovery r
                where r.recovery_id = :recoveryId
                  and re.execution_id = r.execution_id
                """)
            .param("recoveryId", recoveryId)
            .param("externalStatus", result.status().name())
            .param("queriedAt", queriedAt)
            .update();
        return true;
    }

    @Override
    public boolean markManualReview(String recoveryId, String workerId, String reasonCode) {
        return complete(recoveryId, workerId, "MANUAL_REVIEW", reasonCode);
    }

    private boolean complete(String recoveryId, String workerId, String status, String reasonCode) {
        return jdbcClient.sql("""
                update runtime.external_interaction_recovery
                set recovery_status = :status,
                    lease_owner = null,
                    lease_until = null,
                    last_error_code = :reasonCode,
                    updated_at = :updatedAt
                where recovery_id = :recoveryId
                  and recovery_status = 'CLAIMED'
                  and lease_owner = :workerId
                """)
            .param("recoveryId", recoveryId)
            .param("workerId", workerId)
            .param("status", status)
            .param("reasonCode", reasonCode)
            .param("updatedAt", OffsetDateTime.now(clock))
            .update() == 1;
    }
}
