package com.adp.gateway.runtime.infrastructure;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;

import com.adp.gateway.context.domain.CanonicalContext;
import com.adp.gateway.decision.domain.RuntimeDecision;
import com.adp.gateway.policy.domain.ArtifactReference;
import com.adp.gateway.policy.domain.PolicySnapshot;
import com.adp.gateway.runtime.application.RuntimeExecutionPersistence;
import com.adp.gateway.runtime.domain.RuntimeExecutionStatus;
import com.adp.gateway.runtime.domain.RuntimeExecutionTrace;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class JdbcRuntimeExecutionPersistence implements RuntimeExecutionPersistence {

    private final JdbcClient jdbcClient;
    private final Clock clock;

    public JdbcRuntimeExecutionPersistence(JdbcClient jdbcClient, Clock clock) {
        this.jdbcClient = jdbcClient;
        this.clock = clock;
    }

    @Override
    public void recordReceived(RuntimeExecutionTrace trace) {
        jdbcClient.sql("""
            insert into runtime_execution (
                execution_id, request_id, trace_id, idempotency_key, workload_id,
                purpose_code, subject_ref_digest, provider_profile_id, status,
                created_at, updated_at
            )
            values (
                :executionId, :requestId, :traceId, :idempotencyKey, :workloadId,
                :purposeCode, :subjectRefDigest, :providerProfileId, :status,
                :createdAt, :updatedAt
            )
            """)
            .param("executionId", trace.executionId())
            .param("requestId", trace.requestId())
            .param("traceId", trace.traceId())
            .param("idempotencyKey", trace.idempotencyKey())
            .param("workloadId", trace.workloadId())
            .param("purposeCode", trace.purposeCode())
            .param("subjectRefDigest", trace.subjectRefDigest())
            .param("providerProfileId", trace.providerProfileId())
            .param("status", trace.status())
            .param("createdAt", trace.createdAt())
            .param("updatedAt", trace.updatedAt())
            .update();
    }

    @Override
    public void recordPolicyEvaluation(String executionId, PolicySnapshot snapshot) {
        recordPolicySnapshot(snapshot);
        jdbcClient.sql("""
            insert into runtime_policy_evaluation (
                execution_id, policy_version, snapshot_digest,
                source_artifact_id, source_artifact_version,
                source_artifact_digest_algorithm, source_artifact_digest_value,
                policy_action, matched_rule_refs, evidence_refs, required_controls,
                created_at
            )
            values (
                :executionId, :policyVersion, :snapshotDigest,
                :sourceArtifactId, :sourceArtifactVersion,
                :sourceArtifactDigestAlgorithm, :sourceArtifactDigestValue,
                :policyAction, :matchedRuleRefs, :evidenceRefs, :requiredControls,
                :createdAt
            )
            """)
            .param("executionId", executionId)
            .param("policyVersion", snapshot.policyVersion())
            .param("snapshotDigest", snapshot.snapshotDigest())
            .param("sourceArtifactId", snapshot.sourcePolicyEvaluationArtifactRef().artifactId())
            .param("sourceArtifactVersion", snapshot.sourcePolicyEvaluationArtifactRef().artifactVersion())
            .param("sourceArtifactDigestAlgorithm", snapshot.sourcePolicyEvaluationArtifactRef().artifactDigest().algorithm())
            .param("sourceArtifactDigestValue", snapshot.sourcePolicyEvaluationArtifactRef().artifactDigest().value())
            .param("policyAction", snapshot.evaluation().policyAction().name())
            .param("matchedRuleRefs", auditValue(snapshot.evaluation().matchedRuleRefs()))
            .param("evidenceRefs", auditValue(snapshot.evaluation().evidenceRefs()))
            .param("requiredControls", auditValue(snapshot.evaluation().requiredControls()))
            .param("createdAt", OffsetDateTime.now(clock))
            .update();
    }

    private void recordPolicySnapshot(PolicySnapshot snapshot) {
        jdbcClient.sql("""
            insert into policy_snapshot (
                policy_version, snapshot_digest, lifecycle_stage, effective_at,
                source_artifact_id, source_artifact_version,
                source_artifact_digest_algorithm, source_artifact_digest_value,
                policy_action, matched_policy_refs, matched_rule_refs, requirement_refs,
                evidence_refs, required_controls, validation_artifact_refs, created_at
            )
            values (
                :policyVersion, :snapshotDigest, :lifecycleStage, :effectiveAt,
                :sourceArtifactId, :sourceArtifactVersion,
                :sourceArtifactDigestAlgorithm, :sourceArtifactDigestValue,
                :policyAction, :matchedPolicyRefs, :matchedRuleRefs, :requirementRefs,
                :evidenceRefs, :requiredControls, :validationArtifactRefs, :createdAt
            )
            on conflict (snapshot_digest) do nothing
            """)
            .param("policyVersion", snapshot.policyVersion())
            .param("snapshotDigest", snapshot.snapshotDigest())
            .param("lifecycleStage", snapshot.lifecycleStage().name())
            .param("effectiveAt", snapshot.effectiveAt())
            .param("sourceArtifactId", snapshot.sourcePolicyEvaluationArtifactRef().artifactId())
            .param("sourceArtifactVersion", snapshot.sourcePolicyEvaluationArtifactRef().artifactVersion())
            .param("sourceArtifactDigestAlgorithm", snapshot.sourcePolicyEvaluationArtifactRef().artifactDigest().algorithm())
            .param("sourceArtifactDigestValue", snapshot.sourcePolicyEvaluationArtifactRef().artifactDigest().value())
            .param("policyAction", snapshot.evaluation().policyAction().name())
            .param("matchedPolicyRefs", auditValue(snapshot.evaluation().matchedPolicyRefs()))
            .param("matchedRuleRefs", auditValue(snapshot.evaluation().matchedRuleRefs()))
            .param("requirementRefs", auditValue(snapshot.evaluation().requirementRefs()))
            .param("evidenceRefs", auditValue(snapshot.evaluation().evidenceRefs()))
            .param("requiredControls", auditValue(snapshot.evaluation().requiredControls()))
            .param("validationArtifactRefs", auditValue(snapshot.evaluation().validationArtifactRefs()))
            .param("createdAt", OffsetDateTime.now(clock))
            .update();
    }

    @Override
    public void recordRuntimeDecision(String executionId, RuntimeDecision decision) {
        jdbcClient.sql("""
            insert into runtime_decision (
                execution_id, decision_id, policy_action, final_action,
                authorization_result, applicability_result, runtime_context_digest,
                reason_codes, created_at
            )
            values (
                :executionId, :decisionId, :policyAction, :finalAction,
                :authorizationResult, :applicabilityResult, :runtimeContextDigest,
                :reasonCodes, :createdAt
            )
            """)
            .param("executionId", executionId)
            .param("decisionId", decision.decisionId())
            .param("policyAction", decision.policyAction().name())
            .param("finalAction", decision.finalAction().name())
            .param("authorizationResult", decision.authorizationResult().name())
            .param("applicabilityResult", decision.applicabilityResult().name())
            .param("runtimeContextDigest", decision.runtimeContextDigest())
            .param("reasonCodes", decision.runtimeReasonCodes().stream().map(Enum::name).collect(Collectors.joining(",")))
            .param("createdAt", OffsetDateTime.now(clock))
            .update();
        jdbcClient.sql("""
            update runtime_execution
            set runtime_context_digest = :runtimeContextDigest,
                policy_version = :policyVersion,
                snapshot_digest = :snapshotDigest,
                decision_id = :decisionId,
                final_action = :finalAction,
                updated_at = :updatedAt
            where execution_id = :executionId
            """)
            .param("executionId", executionId)
            .param("runtimeContextDigest", decision.runtimeContextDigest())
            .param("policyVersion", decision.policyVersion())
            .param("snapshotDigest", decision.snapshotDigest())
            .param("decisionId", decision.decisionId())
            .param("finalAction", decision.finalAction().name())
            .param("updatedAt", OffsetDateTime.now(clock))
            .update();
    }

    @Override
    public void recordRetrieved(String executionId, CanonicalContext context) {
        jdbcClient.sql("""
            update runtime_execution
            set canonical_context_digest = :canonicalContextDigest,
                updated_at = :updatedAt
            where execution_id = :executionId
            """)
            .param("executionId", executionId)
            .param("canonicalContextDigest", context.contextDigest())
            .param("updatedAt", OffsetDateTime.now(clock))
            .update();
    }

    @Override
    public void updateStatus(String executionId, RuntimeExecutionStatus status) {
        jdbcClient.sql("""
            update runtime_execution
            set status = :status,
                updated_at = :updatedAt
            where execution_id = :executionId
            """)
            .param("executionId", executionId)
            .param("status", status.name())
            .param("updatedAt", OffsetDateTime.now(clock))
            .update();
    }

    @Override
    public RuntimeExecutionTrace load(String executionId) {
        return jdbcClient.sql("""
            select execution_id, request_id, trace_id, idempotency_key, workload_id,
                   purpose_code, subject_ref_digest, provider_profile_id,
                   canonical_context_digest, runtime_context_digest,
                   policy_version, snapshot_digest, decision_id, final_action,
                   status, created_at, updated_at
            from runtime_execution
            where execution_id = :executionId
            """)
            .param("executionId", executionId)
            .query(RuntimeExecutionTrace.class)
            .single();
    }

    private String auditValue(List<ArtifactReference> references) {
        return references.stream()
            .map(ArtifactReference::auditValue)
            .collect(Collectors.joining(","));
    }
}
