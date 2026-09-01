package com.adp.gateway.runtime.infrastructure;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;

import com.adp.gateway.connector.domain.ConnectorResult;
import com.adp.gateway.context.domain.CanonicalContext;
import com.adp.gateway.decision.domain.RuntimeDecision;
import com.adp.gateway.egress.domain.OutboundCandidatePayload;
import com.adp.gateway.egress.domain.OutboundGuardResult;
import com.adp.gateway.egress.domain.ResponseGuardResult;
import com.adp.gateway.policy.domain.ArtifactReference;
import com.adp.gateway.policy.domain.PolicySnapshot;
import com.adp.gateway.runtime.application.DuplicateRuntimeExecutionException;
import com.adp.gateway.runtime.application.RuntimeExecutionNotFoundException;
import com.adp.gateway.runtime.application.RuntimeExecutionPersistence;
import com.adp.gateway.runtime.domain.RuntimeExecutionStatus;
import com.adp.gateway.runtime.domain.RuntimeExecutionTrace;
import com.adp.gateway.transform.domain.TransformFieldResult;
import com.adp.gateway.transform.domain.TransformResult;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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
        try {
            jdbcClient.sql("""
                insert into runtime.runtime_execution (
                    execution_id, request_id, trace_id, idempotency_key, workload_id,
                    purpose_code, subject_ref_digest, provider_profile_id, input_digest, status,
                    created_at, updated_at
                )
                values (
                    :executionId, :requestId, :traceId, :idempotencyKey, :workloadId,
                    :purposeCode, :subjectRefDigest, :providerProfileId, :inputDigest, :status,
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
                .param("inputDigest", trace.inputDigest())
                .param("status", trace.status())
                .param("createdAt", trace.createdAt())
                .param("updatedAt", trace.updatedAt())
                .update();
        } catch (DuplicateKeyException exception) {
            throw new DuplicateRuntimeExecutionException("Idempotency key already used for workload");
        }
    }

    @Override
    public void recordPolicyEvaluation(String executionId, PolicySnapshot snapshot) {
        recordPolicySnapshot(snapshot);
        jdbcClient.sql("""
            insert into runtime.policy_evaluation (
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
            insert into governance.policy_snapshot (
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
            insert into runtime.runtime_decision (
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
            update runtime.runtime_execution
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
    @Transactional
    public void recordTransform(String executionId, RuntimeDecision decision, TransformResult transformResult) {
        jdbcClient.sql("""
            insert into runtime.transform_execution (
                transform_execution_id, execution_id, decision_id, status,
                output_digest, field_count, created_at
            )
            values (
                :transformExecutionId, :executionId, :decisionId, :status,
                :outputDigest, :fieldCount, :createdAt
            )
            """)
            .param("transformExecutionId", transformResult.transformExecutionId())
            .param("executionId", executionId)
            .param("decisionId", decision.decisionId())
            .param("status", transformResult.status())
            .param("outputDigest", transformResult.outputDigest())
            .param("fieldCount", transformResult.fields().size())
            .param("createdAt", OffsetDateTime.now(clock))
            .update();
        for (TransformFieldResult field : transformResult.fields()) {
            recordTransformField(transformResult.transformExecutionId(), field);
        }
        jdbcClient.sql("""
            update runtime.runtime_execution
            set transform_execution_id = :transformExecutionId,
                transform_status = :transformStatus,
                transform_output_digest = :transformOutputDigest,
                updated_at = :updatedAt
            where execution_id = :executionId
            """)
            .param("executionId", executionId)
            .param("transformExecutionId", transformResult.transformExecutionId())
            .param("transformStatus", transformResult.status())
            .param("transformOutputDigest", transformResult.outputDigest())
            .param("updatedAt", OffsetDateTime.now(clock))
            .update();
    }

    private void recordTransformField(String transformExecutionId, TransformFieldResult field) {
        jdbcClient.sql("""
            insert into runtime.transform_field (
                transform_execution_id, field_path, dataset_name, field_name, data_class,
                strategy, strategy_version, key_version, mapping_version, instruction_digest,
                source_value_digest, transformed_value_digest, token_ref, created_at
            )
            values (
                :transformExecutionId, :fieldPath, :datasetName, :fieldName, :dataClass,
                :strategy, :strategyVersion, :keyVersion, :mappingVersion, :instructionDigest,
                :sourceValueDigest, :transformedValueDigest, :tokenRef, :createdAt
            )
            """)
            .param("transformExecutionId", transformExecutionId)
            .param("fieldPath", field.path())
            .param("datasetName", field.datasetName())
            .param("fieldName", field.fieldName())
            .param("dataClass", field.dataClass().name())
            .param("strategy", field.strategy().name())
            .param("strategyVersion", field.strategyVersion())
            .param("keyVersion", field.keyVersion())
            .param("mappingVersion", field.mappingVersion())
            .param("instructionDigest", field.instructionDigest())
            .param("sourceValueDigest", field.sourceValueDigest())
            .param("transformedValueDigest", field.transformedValueDigest())
            .param("tokenRef", field.tokenRef())
            .param("createdAt", OffsetDateTime.now(clock))
            .update();
    }

    @Override
    public void recordOutbound(String executionId, OutboundCandidatePayload payload, OutboundGuardResult guardResult) {
        jdbcClient.sql("""
            insert into runtime.outbound_candidate (
                outbound_payload_id, execution_id, destination_profile_id,
                destination_profile_version, destination_profile_digest, pack_type,
                schema_version, candidate_payload_digest, field_count, guard_status,
                guard_reason_codes, created_at
            )
            values (
                :outboundPayloadId, :executionId, :destinationProfileId,
                :destinationProfileVersion, :destinationProfileDigest, :packType,
                :schemaVersion, :candidatePayloadDigest, :fieldCount, :guardStatus,
                :guardReasonCodes, :createdAt
            )
            """)
            .param("outboundPayloadId", payload.outboundPayloadId())
            .param("executionId", executionId)
            .param("destinationProfileId", payload.destinationProfileId())
            .param("destinationProfileVersion", payload.destinationProfileVersion())
            .param("destinationProfileDigest", payload.destinationProfileDigest())
            .param("packType", payload.packType().name())
            .param("schemaVersion", payload.schemaVersion())
            .param("candidatePayloadDigest", payload.candidatePayloadDigest())
            .param("fieldCount", payload.fieldCount())
            .param("guardStatus", guardResult.status())
            .param("guardReasonCodes", String.join(",", guardResult.reasonCodes()))
            .param("createdAt", OffsetDateTime.now(clock))
            .update();
        jdbcClient.sql("""
            update runtime.runtime_execution
            set outbound_payload_id = :outboundPayloadId,
                outbound_payload_digest = :outboundPayloadDigest,
                outbound_guard_status = :outboundGuardStatus,
                updated_at = :updatedAt
            where execution_id = :executionId
            """)
            .param("executionId", executionId)
            .param("outboundPayloadId", payload.outboundPayloadId())
            .param("outboundPayloadDigest", payload.candidatePayloadDigest())
            .param("outboundGuardStatus", guardResult.status())
            .param("updatedAt", OffsetDateTime.now(clock))
            .update();
    }

    @Override
    public void recordConnector(String executionId, ConnectorResult connectorResult) {
        jdbcClient.sql("""
            insert into runtime.connector_execution (
                connector_execution_id, execution_id, outbound_payload_id,
                outbound_payload_digest, connector_id, status,
                response_digest, response_schema_version, created_at
            )
            values (
                :connectorExecutionId, :executionId, :outboundPayloadId,
                :outboundPayloadDigest, :connectorId, :status,
                :responseDigest, :responseSchemaVersion, :createdAt
            )
            """)
            .param("connectorExecutionId", connectorResult.connectorExecutionId())
            .param("executionId", executionId)
            .param("outboundPayloadId", connectorResult.outboundPayloadId())
            .param("outboundPayloadDigest", connectorResult.outboundPayloadDigest())
            .param("connectorId", connectorResult.connectorId())
            .param("status", connectorResult.status())
            .param("responseDigest", connectorResult.responseDigest())
            .param("responseSchemaVersion", connectorResult.responseSchemaVersion())
            .param("createdAt", OffsetDateTime.now(clock))
            .update();
        jdbcClient.sql("""
            update runtime.runtime_execution
            set connector_execution_id = :connectorExecutionId,
                connector_status = :connectorStatus,
                updated_at = :updatedAt
            where execution_id = :executionId
            """)
            .param("executionId", executionId)
            .param("connectorExecutionId", connectorResult.connectorExecutionId())
            .param("connectorStatus", connectorResult.status())
            .param("updatedAt", OffsetDateTime.now(clock))
            .update();
    }

    @Override
    public void recordResponseGuard(String executionId, ConnectorResult connectorResult, ResponseGuardResult responseGuardResult) {
        jdbcClient.sql("""
            insert into runtime.response_guard_result (
                connector_execution_id, execution_id, connector_id, connector_status,
                status, leakage_detected, reason_codes, created_at
            )
            values (
                :connectorExecutionId, :executionId, :connectorId, :connectorStatus,
                :status, :leakageDetected, :reasonCodes, :createdAt
            )
            """)
            .param("connectorExecutionId", connectorResult.connectorExecutionId())
            .param("executionId", executionId)
            .param("connectorId", connectorResult.connectorId())
            .param("connectorStatus", connectorResult.status())
            .param("status", responseGuardResult.status())
            .param("leakageDetected", responseGuardResult.leakageDetected())
            .param("reasonCodes", String.join(",", responseGuardResult.reasonCodes()))
            .param("createdAt", OffsetDateTime.now(clock))
            .update();
        jdbcClient.sql("""
            update runtime.runtime_execution
            set response_guard_status = :responseGuardStatus,
                updated_at = :updatedAt
            where execution_id = :executionId
            """)
            .param("executionId", executionId)
            .param("responseGuardStatus", responseGuardResult.status())
            .param("updatedAt", OffsetDateTime.now(clock))
            .update();
    }

    @Override
    public void recordRetrieved(String executionId, CanonicalContext context) {
        jdbcClient.sql("""
            update runtime.runtime_execution
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
            update runtime.runtime_execution
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
                   purpose_code, subject_ref_digest, provider_profile_id, input_digest,
                   canonical_context_digest, runtime_context_digest,
                   policy_version, snapshot_digest, decision_id, final_action,
                   transform_execution_id, transform_status, transform_output_digest,
                   outbound_payload_id, outbound_payload_digest, outbound_guard_status,
                   connector_execution_id, connector_status, response_guard_status,
                   status, created_at, updated_at
            from runtime.runtime_execution
            where execution_id = :executionId
            """)
            .param("executionId", executionId)
            .query(RuntimeExecutionTrace.class)
            .optional()
            .orElseThrow(() -> new RuntimeExecutionNotFoundException(executionId));
    }

    private String auditValue(List<ArtifactReference> references) {
        return references.stream()
            .map(ArtifactReference::auditValue)
            .collect(Collectors.joining(","));
    }
}
