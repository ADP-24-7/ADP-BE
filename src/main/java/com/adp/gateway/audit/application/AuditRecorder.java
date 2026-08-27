package com.adp.gateway.audit.application;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.adp.gateway.audit.domain.AuditContext;
import com.adp.gateway.common.contract.RuntimeRequestContext;
import com.adp.gateway.connector.domain.ConnectorResult;
import com.adp.gateway.decision.domain.DecisionResult;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class AuditRecorder {

    private final JdbcClient jdbcClient;
    private final Clock clock;

    public AuditRecorder(JdbcClient jdbcClient, Clock clock) {
        this.jdbcClient = jdbcClient;
        this.clock = clock;
    }

    public AuditContext record(
        RuntimeRequestContext context,
        DecisionResult decisionResult,
        ConnectorResult connectorResult
    ) {
        AuditContext auditContext = new AuditContext(
            "aud_" + UUID.randomUUID(),
            context.requestId(),
            context.traceId(),
            context.idempotencyKey(),
            context.workloadId(),
            decisionResult.decisionId(),
            decisionResult.reasonCode().name(),
            connectorResult.status(),
            OffsetDateTime.now(clock)
        );

        jdbcClient.sql("""
            insert into audit_event (
                audit_id, request_id, trace_id, idempotency_key, workload_id,
                decision_id, reason_code, connector_status, created_at
            )
            values (
                :auditId, :requestId, :traceId, :idempotencyKey, :workloadId,
                :decisionId, :reasonCode, :connectorStatus, :createdAt
            )
            """)
            .param("auditId", auditContext.auditId())
            .param("requestId", auditContext.requestId())
            .param("traceId", auditContext.traceId())
            .param("idempotencyKey", auditContext.idempotencyKey())
            .param("workloadId", auditContext.workloadId())
            .param("decisionId", auditContext.decisionId())
            .param("reasonCode", auditContext.reasonCode())
            .param("connectorStatus", auditContext.connectorStatus())
            .param("createdAt", auditContext.createdAt())
            .update();

        return auditContext;
    }
}
