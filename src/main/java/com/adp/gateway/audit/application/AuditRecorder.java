package com.adp.gateway.audit.application;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.stream.Collectors;
import java.util.UUID;

import com.adp.gateway.audit.domain.AuditContext;
import com.adp.gateway.common.contract.RuntimeRequestContext;
import com.adp.gateway.connector.domain.ConnectorResult;
import com.adp.gateway.decision.domain.RuntimeDecision;
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
        RuntimeDecision decision,
        ConnectorResult connectorResult
    ) {
        String matchedRuleIds = String.join(",", decision.matchedRuleIds());
        AuditContext auditContext = new AuditContext(
            "aud_" + UUID.randomUUID(),
            context.requestId(),
            context.traceId(),
            context.idempotencyKey(),
            context.workloadId(),
            decision.decisionId(),
            decision.sourceArtifactId(),
            decision.policyAction().name(),
            decision.finalAction().name(),
            decision.authorizationResult().name(),
            decision.applicabilityResult().name(),
            matchedRuleIds,
            decision.policyVersion(),
            decision.snapshotDigest(),
            decision.runtimeReasonCodes().stream()
                .map(Enum::name)
                .collect(Collectors.joining(",")),
            connectorResult.status(),
            OffsetDateTime.now(clock)
        );

        jdbcClient.sql("""
            insert into audit_event (
                audit_id, request_id, trace_id, idempotency_key, workload_id,
                decision_id, policy_artifact_id, policy_action, final_action,
                authorization_result, applicability_result, matched_rule_ids,
                policy_version, policy_digest, reason_code, connector_status, created_at
            )
            values (
                :auditId, :requestId, :traceId, :idempotencyKey, :workloadId,
                :decisionId, :policyArtifactId, :policyAction, :finalAction,
                :authorizationResult, :applicabilityResult, :matchedRuleIds,
                :policyVersion, :policyDigest, :reasonCode, :connectorStatus, :createdAt
            )
            """)
            .param("auditId", auditContext.auditId())
            .param("requestId", auditContext.requestId())
            .param("traceId", auditContext.traceId())
            .param("idempotencyKey", auditContext.idempotencyKey())
            .param("workloadId", auditContext.workloadId())
            .param("decisionId", auditContext.decisionId())
            .param("policyArtifactId", auditContext.policyArtifactId())
            .param("policyAction", auditContext.policyAction())
            .param("finalAction", auditContext.finalAction())
            .param("authorizationResult", auditContext.authorizationResult())
            .param("applicabilityResult", auditContext.applicabilityResult())
            .param("matchedRuleIds", auditContext.matchedRuleIds())
            .param("policyVersion", auditContext.policyVersion())
            .param("policyDigest", auditContext.policyDigest())
            .param("reasonCode", auditContext.reasonCode())
            .param("connectorStatus", auditContext.connectorStatus())
            .param("createdAt", auditContext.createdAt())
            .update();

        return auditContext;
    }
}
