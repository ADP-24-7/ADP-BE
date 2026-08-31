package com.adp.gateway.audit.application;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;
import java.util.UUID;

import com.adp.gateway.audit.domain.AuditContext;
import com.adp.gateway.common.contract.RuntimeRequestContext;
import com.adp.gateway.connector.domain.ConnectorResult;
import com.adp.gateway.decision.domain.RuntimeDecision;
import com.adp.gateway.policy.domain.ArtifactReference;
import com.adp.gateway.policy.domain.SourcePolicyEvaluationArtifactRef;
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
        String matchedRuleIds = auditValue(decision.matchedRuleRefs());
        String evidenceRefs = auditValue(decision.evidenceRefs());
        String requiredControls = auditValue(decision.requiredControls());
        SourcePolicyEvaluationArtifactRef sourceArtifact = decision.sourcePolicyEvaluationArtifactRef();
        AuditContext auditContext = new AuditContext(
            "aud_" + UUID.randomUUID(),
            context.requestId(),
            context.traceId(),
            context.idempotencyKey(),
            context.workloadId(),
            decision.decisionId(),
            sourceArtifact.artifactId(),
            sourceArtifact.artifactVersion(),
            sourceArtifact.artifactDigest().algorithm(),
            sourceArtifact.artifactDigest().value(),
            decision.policyAction().name(),
            decision.finalAction().name(),
            decision.authorizationResult().name(),
            decision.applicabilityResult().name(),
            decision.runtimeContextDigest(),
            matchedRuleIds,
            evidenceRefs,
            requiredControls,
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
                decision_id, policy_artifact_id, policy_artifact_version,
                policy_artifact_digest_algorithm, policy_artifact_digest_value,
                policy_action, final_action,
                authorization_result, applicability_result, runtime_context_digest,
                matched_rule_ids, evidence_refs, required_controls,
                policy_version, policy_digest, reason_code, connector_status, created_at
            )
            values (
                :auditId, :requestId, :traceId, :idempotencyKey, :workloadId,
                :decisionId, :policyArtifactId, :policyArtifactVersion,
                :policyArtifactDigestAlgorithm, :policyArtifactDigestValue,
                :policyAction, :finalAction,
                :authorizationResult, :applicabilityResult, :runtimeContextDigest,
                :matchedRuleIds, :evidenceRefs, :requiredControls,
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
            .param("policyArtifactVersion", auditContext.policyArtifactVersion())
            .param("policyArtifactDigestAlgorithm", auditContext.policyArtifactDigestAlgorithm())
            .param("policyArtifactDigestValue", auditContext.policyArtifactDigestValue())
            .param("policyAction", auditContext.policyAction())
            .param("finalAction", auditContext.finalAction())
            .param("authorizationResult", auditContext.authorizationResult())
            .param("applicabilityResult", auditContext.applicabilityResult())
            .param("runtimeContextDigest", auditContext.runtimeContextDigest())
            .param("matchedRuleIds", auditContext.matchedRuleIds())
            .param("evidenceRefs", auditContext.evidenceRefs())
            .param("requiredControls", auditContext.requiredControls())
            .param("policyVersion", auditContext.policyVersion())
            .param("policyDigest", auditContext.policyDigest())
            .param("reasonCode", auditContext.reasonCode())
            .param("connectorStatus", auditContext.connectorStatus())
            .param("createdAt", auditContext.createdAt())
            .update();

        return auditContext;
    }

    private String auditValue(List<ArtifactReference> references) {
        return references.stream()
            .map(ArtifactReference::auditValue)
            .collect(Collectors.joining(","));
    }
}
