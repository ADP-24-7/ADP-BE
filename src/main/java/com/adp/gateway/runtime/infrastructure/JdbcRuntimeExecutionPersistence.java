package com.adp.gateway.runtime.infrastructure;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.adp.gateway.connector.domain.ConnectorResult;
import com.adp.gateway.context.domain.CanonicalContext;
import com.adp.gateway.decision.domain.RuntimeDecision;
import com.adp.gateway.decision.domain.ExecutionPackPolicyEvaluation;
import com.adp.gateway.egress.domain.DestinationProfile;
import com.adp.gateway.egress.domain.OutboundCandidatePayload;
import com.adp.gateway.egress.domain.OutboundGuardResult;
import com.adp.gateway.egress.domain.ResponseGuardResult;
import com.adp.gateway.egress.domain.ProviderRequestPayload;
import com.adp.gateway.policy.domain.ArtifactReference;
import com.adp.gateway.policy.domain.PolicySnapshot;
import com.adp.gateway.runtime.application.DuplicateRuntimeExecutionException;
import com.adp.gateway.runtime.application.IdempotentExecutionReplay;
import com.adp.gateway.runtime.application.RuntimeExecutionNotFoundException;
import com.adp.gateway.runtime.application.RuntimeExecutionPersistence;
import com.adp.gateway.runtime.domain.RuntimeExecutionStatus;
import com.adp.gateway.runtime.domain.RuntimeExecutionTrace;
import com.adp.gateway.runtime.domain.ControlledDeliveryResult;
import com.adp.gateway.recovery.application.ExternalInteractionRecoveryPersistence;
import com.adp.gateway.policyharness.domain.PolicyHarnessBinding;
import com.adp.gateway.policyharness.domain.PolicyLayerReference;
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
    private final ExternalInteractionRecoveryPersistence recoveryPersistence;

    public JdbcRuntimeExecutionPersistence(
        JdbcClient jdbcClient,
        Clock clock,
        ExternalInteractionRecoveryPersistence recoveryPersistence
    ) {
        this.jdbcClient = jdbcClient;
        this.clock = clock;
        this.recoveryPersistence = recoveryPersistence;
    }

    @Override
    public void recordReceived(
        RuntimeExecutionTrace trace,
        String idempotencyInstitutionId,
        String requestHash
    ) {
        try {
            jdbcClient.sql("""
                insert into runtime.runtime_execution (
                    execution_id, request_id, trace_id, idempotency_key, workload_id,
                    idempotency_institution_id, request_hash,
                    purpose_code, subject_ref_digest, provider_profile_id,
                    destination_profile_id, destination_profile_version, destination_profile_digest,
                    institution_id, approval_reference,
                    input_digest, status,
                    created_at, updated_at
                )
                values (
                    :executionId, :requestId, :traceId, :idempotencyKey, :workloadId,
                    :idempotencyInstitutionId, :requestHash,
                    :purposeCode, :subjectRefDigest, :providerProfileId,
                    :destinationProfileId, :destinationProfileVersion, :destinationProfileDigest,
                    :institutionId, :approvalReference,
                    :inputDigest, :status,
                    :createdAt, :updatedAt
                )
                """)
                .param("executionId", trace.executionId())
                .param("requestId", trace.requestId())
                .param("traceId", trace.traceId())
                .param("idempotencyKey", trace.idempotencyKey())
                .param("workloadId", trace.workloadId())
                .param("idempotencyInstitutionId", idempotencyInstitutionId)
                .param("requestHash", requestHash)
                .param("purposeCode", trace.purposeCode())
                .param("subjectRefDigest", trace.subjectRefDigest())
                .param("providerProfileId", trace.providerProfileId())
                .param("destinationProfileId", trace.destinationProfileId())
                .param("destinationProfileVersion", trace.destinationProfileVersion())
                .param("destinationProfileDigest", trace.destinationProfileDigest())
                .param("institutionId", trace.institutionId())
                .param("approvalReference", trace.approvalReference())
                .param("inputDigest", trace.inputDigest())
                .param("status", trace.status())
                .param("createdAt", trace.createdAt())
                .param("updatedAt", trace.updatedAt())
                .update();
        } catch (DuplicateKeyException exception) {
            throw new DuplicateRuntimeExecutionException("Idempotency key already used for institution and workload");
        }
    }

    @Override
    public Optional<IdempotentExecutionReplay> findIdempotentExecution(
        String institutionId,
        String workloadId,
        String idempotencyKey
    ) {
        return jdbcClient.sql("""
            select re.execution_id,
                   re.request_hash,
                   re.status,
                   rd.decision_id,
                   rd.policy_action,
                   rd.final_action,
                   rd.authorization_result,
                   rd.applicability_result,
                   rd.runtime_context_digest,
                   re.policy_version,
                   re.snapshot_digest,
                   pe.source_artifact_id,
                   pe.source_artifact_version,
                   pe.source_artifact_digest_algorithm,
                   pe.source_artifact_digest_value,
                   re.transform_execution_id,
                   re.transform_status,
                   re.transform_output_digest,
                   re.transformed_field_count,
                   re.outbound_candidate_digest,
                   re.outbound_guard_status,
                   re.connector_status,
                   re.response_guard_status,
                   re.controlled_delivery_status,
                   re.controlled_delivery_response_digest,
                   (select ae.audit_id
                    from audit_event ae
                    where ae.decision_id = rd.decision_id
                    order by ae.created_at desc
                    limit 1) as audit_id
            from runtime.runtime_execution re
            left join runtime.runtime_decision rd on rd.execution_id = re.execution_id
            left join runtime.policy_evaluation pe on pe.execution_id = re.execution_id
            where re.idempotency_institution_id = :institutionId
              and re.workload_id = :workloadId
              and re.idempotency_key = :idempotencyKey
            """)
            .param("institutionId", institutionId)
            .param("workloadId", workloadId)
            .param("idempotencyKey", idempotencyKey)
            .query(IdempotentExecutionReplay.class)
            .optional();
    }

    @Override
    public void recordDestinationProfile(String executionId, DestinationProfile destinationProfile) {
        jdbcClient.sql("""
            update runtime.runtime_execution
            set provider_profile_id = :providerProfileId,
                destination_profile_id = :destinationProfileId,
                destination_profile_version = :destinationProfileVersion,
                destination_profile_digest = :destinationProfileDigest,
                destination_tenant_id = :destinationTenantId,
                destination_region = :destinationRegion,
                destination_retention_policy = :destinationRetentionPolicy,
                destination_training_use_allowed = :destinationTrainingUseAllowed,
                updated_at = :updatedAt
            where execution_id = :executionId
            """)
            .param("executionId", executionId)
            .param("providerProfileId", destinationProfile.providerProfileId())
            .param("destinationProfileId", destinationProfile.destinationProfileId())
            .param("destinationProfileVersion", destinationProfile.profileVersion())
            .param("destinationProfileDigest", destinationProfile.profileDigest())
            .param("destinationTenantId", destinationProfile.tenantId())
            .param("destinationRegion", destinationProfile.region())
            .param("destinationRetentionPolicy", destinationProfile.retentionPolicy())
            .param("destinationTrainingUseAllowed", destinationProfile.trainingUseAllowed())
            .param("updatedAt", OffsetDateTime.now(clock))
            .update();
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
    public void recordExecutionPackPolicyEvaluation(
        String executionId,
        ExecutionPackPolicyEvaluation evaluation
    ) {
        jdbcClient.sql("""
            insert into runtime.execution_pack_policy_evaluation (
                execution_id, pack_type, profile_id, profile_version,
                profile_digest, result, reason_codes, evaluated_at
            ) values (
                :executionId, :packType, :profileId, :profileVersion,
                :profileDigest, :result, :reasonCodes, :evaluatedAt
            )
            """)
            .param("executionId", executionId)
            .param("packType", evaluation.packType().name())
            .param("profileId", evaluation.profileId())
            .param("profileVersion", evaluation.profileVersion())
            .param("profileDigest", evaluation.profileDigest())
            .param("result", evaluation.result())
            .param("reasonCodes", evaluation.reasonCodes().stream()
                .map(Enum::name).collect(Collectors.joining(",")))
            .param("evaluatedAt", OffsetDateTime.now(clock))
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
                outbound_candidate_digest = :outboundCandidateDigest,
                outbound_guard_status = :outboundGuardStatus,
                updated_at = :updatedAt
            where execution_id = :executionId
            """)
            .param("executionId", executionId)
            .param("outboundPayloadId", payload.outboundPayloadId())
            .param("outboundCandidateDigest", payload.candidatePayloadDigest())
            .param("outboundGuardStatus", guardResult.status())
            .param("updatedAt", OffsetDateTime.now(clock))
            .update();
    }

    @Override
    @Transactional
    public void recordPolicyHarness(String executionId, PolicyHarnessBinding binding) {
        var lineage = binding.fieldLineage();
        jdbcClient.sql("""
            insert into runtime.policy_harness_binding (
                execution_id, institution_id, approval_reference, approval_version,
                approval_scope_digest, approval_reuse_status, reason_codes,
                policy_layers, policy_layers_digest,
                requested_fields, requested_fields_digest,
                retrieved_fields, retrieved_fields_digest,
                transformed_fields, transformed_fields_digest,
                released_fields, released_fields_digest, created_at
            ) values (
                :executionId, :institutionId, :approvalReference, :approvalVersion,
                :approvalScopeDigest, :approvalReuseStatus, :reasonCodes,
                :policyLayers, :policyLayersDigest,
                :requestedFields, :requestedFieldsDigest,
                :retrievedFields, :retrievedFieldsDigest,
                :transformedFields, :transformedFieldsDigest,
                :releasedFields, :releasedFieldsDigest, :createdAt
            )
            """)
            .param("executionId", executionId)
            .param("institutionId", binding.institutionId())
            .param("approvalReference", binding.approvalReference())
            .param("approvalVersion", binding.approvalVersion())
            .param("approvalScopeDigest", binding.approvalScopeDigest())
            .param("approvalReuseStatus", binding.approvalReuseStatus().name())
            .param("reasonCodes", String.join(",", binding.reasonCodes()))
            .param("policyLayers", binding.policyLayers().stream()
                .map(this::policyLayerAuditValue)
                .collect(Collectors.joining(",")))
            .param("policyLayersDigest", binding.policyLayersDigest())
            .param("requestedFields", auditFields(lineage.requestedFields()))
            .param("requestedFieldsDigest", lineage.requestedFieldsDigest())
            .param("retrievedFields", auditFields(lineage.retrievedFields()))
            .param("retrievedFieldsDigest", lineage.retrievedFieldsDigest())
            .param("transformedFields", auditFields(lineage.transformedFields()))
            .param("transformedFieldsDigest", lineage.transformedFieldsDigest())
            .param("releasedFields", auditFields(lineage.releasedFields()))
            .param("releasedFieldsDigest", lineage.releasedFieldsDigest())
            .param("createdAt", OffsetDateTime.now(clock))
            .update();
        jdbcClient.sql("""
            update runtime.runtime_execution
            set institution_id = :institutionId,
                approval_reference = :approvalReference,
                approval_version = :approvalVersion,
                approval_scope_digest = :approvalScopeDigest,
                approval_reuse_status = :approvalReuseStatus,
                policy_layers_digest = :policyLayersDigest,
                requested_fields_digest = :requestedFieldsDigest,
                requested_field_count = :requestedFieldCount,
                retrieved_fields_digest = :retrievedFieldsDigest,
                retrieved_field_count = :retrievedFieldCount,
                transformed_fields_digest = :transformedFieldsDigest,
                transformed_field_count = :transformedFieldCount,
                released_fields_digest = :releasedFieldsDigest,
                released_field_count = :releasedFieldCount,
                updated_at = :updatedAt
            where execution_id = :executionId
            """)
            .param("executionId", executionId)
            .param("institutionId", binding.institutionId())
            .param("approvalReference", binding.approvalReference())
            .param("approvalVersion", binding.approvalVersion())
            .param("approvalScopeDigest", binding.approvalScopeDigest())
            .param("approvalReuseStatus", binding.approvalReuseStatus().name())
            .param("policyLayersDigest", binding.policyLayersDigest())
            .param("requestedFieldsDigest", lineage.requestedFieldsDigest())
            .param("requestedFieldCount", lineage.requestedFields().size())
            .param("retrievedFieldsDigest", lineage.retrievedFieldsDigest())
            .param("retrievedFieldCount", lineage.retrievedFields().size())
            .param("transformedFieldsDigest", lineage.transformedFieldsDigest())
            .param("transformedFieldCount", lineage.transformedFields().size())
            .param("releasedFieldsDigest", lineage.releasedFieldsDigest())
            .param("releasedFieldCount", lineage.releasedFields().size())
            .param("updatedAt", OffsetDateTime.now(clock))
            .update();
    }

    @Override
    @Transactional
    public void recordProviderRequest(
        String executionId,
        DestinationProfile destinationProfile,
        ProviderRequestPayload providerRequest
    ) {
        jdbcClient.sql("""
            insert into runtime.provider_request (
                provider_request_id, execution_id, outbound_payload_id,
                provider_profile_id, destination_profile_id, destination_profile_version,
                tenant_id, region, retention_policy, training_use_allowed,
                schema_version, canonical_payload_digest, field_count, created_at
            ) values (
                :providerRequestId, :executionId, :outboundPayloadId,
                :providerProfileId, :destinationProfileId, :destinationProfileVersion,
                :tenantId, :region, :retentionPolicy, :trainingUseAllowed,
                :schemaVersion, :canonicalPayloadDigest, :fieldCount, :createdAt
            )
            """)
            .param("providerRequestId", providerRequest.providerRequestId())
            .param("executionId", executionId)
            .param("outboundPayloadId", providerRequest.outboundPayloadId())
            .param("providerProfileId", providerRequest.providerProfileId())
            .param("destinationProfileId", destinationProfile.destinationProfileId())
            .param("destinationProfileVersion", destinationProfile.profileVersion())
            .param("tenantId", destinationProfile.tenantId())
            .param("region", destinationProfile.region())
            .param("retentionPolicy", destinationProfile.retentionPolicy())
            .param("trainingUseAllowed", destinationProfile.trainingUseAllowed())
            .param("schemaVersion", providerRequest.schemaVersion())
            .param("canonicalPayloadDigest", providerRequest.canonicalPayloadDigest())
            .param("fieldCount", providerRequest.fieldCount())
            .param("createdAt", OffsetDateTime.now(clock))
            .update();
        jdbcClient.sql("""
            update runtime.runtime_execution
            set provider_request_id = :providerRequestId,
                provider_request_digest = :providerRequestDigest,
                updated_at = :updatedAt
            where execution_id = :executionId
            """)
            .param("executionId", executionId)
            .param("providerRequestId", providerRequest.providerRequestId())
            .param("providerRequestDigest", providerRequest.canonicalPayloadDigest())
            .param("updatedAt", OffsetDateTime.now(clock))
            .update();
    }

    @Override
    @Transactional
    public void recordConnector(String executionId, ConnectorResult connectorResult) {
        jdbcClient.sql("""
            insert into runtime.connector_execution (
                connector_execution_id, execution_id, outbound_payload_id,
                outbound_candidate_digest, connector_id, status,
                response_digest, response_schema_version, created_at
            )
            values (
                :connectorExecutionId, :executionId, :outboundPayloadId,
                :outboundCandidateDigest, :connectorId, :status,
                :responseDigest, :responseSchemaVersion, :createdAt
            )
            """)
            .param("connectorExecutionId", connectorResult.connectorExecutionId())
            .param("executionId", executionId)
            .param("outboundPayloadId", connectorResult.outboundPayloadId())
            .param("outboundCandidateDigest", connectorResult.outboundCandidateDigest())
            .param("connectorId", connectorResult.connectorId())
            .param("status", connectorResult.status().name())
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
            .param("connectorStatus", connectorResult.status().name())
            .param("updatedAt", OffsetDateTime.now(clock))
            .update();
        if (connectorResult.status() == com.adp.gateway.connector.domain.ConnectorStatus.SENT_UNKNOWN) {
            recoveryPersistence.scheduleUnknown(executionId, connectorResult, OffsetDateTime.now(clock));
        }
    }

    @Override
    @Transactional
    public void recordResponseGuard(String executionId, ConnectorResult connectorResult, ResponseGuardResult responseGuardResult) {
        jdbcClient.sql("""
            insert into runtime.response_guard_result (
                connector_execution_id, execution_id, connector_id, connector_status,
                status, leakage_detected, reason_codes, response_digest,
                detector_version, finding_count, created_at
            )
            values (
                :connectorExecutionId, :executionId, :connectorId, :connectorStatus,
                :status, :leakageDetected, :reasonCodes, :responseDigest,
                :detectorVersion, :findingCount, :createdAt
            )
            """)
            .param("connectorExecutionId", connectorResult.connectorExecutionId())
            .param("executionId", executionId)
            .param("connectorId", connectorResult.connectorId())
            .param("connectorStatus", connectorResult.status().name())
            .param("status", responseGuardResult.status())
            .param("leakageDetected", responseGuardResult.leakageDetected())
            .param("reasonCodes", String.join(",", responseGuardResult.reasonCodes()))
            .param("responseDigest", connectorResult.responseDigest())
            .param("detectorVersion", responseGuardResult.detectorVersion())
            .param("findingCount", responseGuardResult.findings().size())
            .param("createdAt", OffsetDateTime.now(clock))
            .update();
        responseGuardResult.findings().forEach(finding -> jdbcClient.sql("""
            insert into runtime.response_sensitive_finding (
                connector_execution_id, execution_id, finding_type, location,
                start_offset, end_offset, detector_version, evidence_digest, created_at
            ) values (
                :connectorExecutionId, :executionId, :findingType, :location,
                :startOffset, :endOffset, :detectorVersion, :evidenceDigest, :createdAt
            )
            """)
            .param("connectorExecutionId", connectorResult.connectorExecutionId())
            .param("executionId", executionId)
            .param("findingType", finding.findingType())
            .param("location", finding.location())
            .param("startOffset", finding.startOffset())
            .param("endOffset", finding.endOffset())
            .param("detectorVersion", finding.detectorVersion())
            .param("evidenceDigest", finding.evidenceDigest())
            .param("createdAt", OffsetDateTime.now(clock))
            .update());
        jdbcClient.sql("""
            update runtime.runtime_execution
            set response_guard_status = :responseGuardStatus,
                provider_response_digest = :providerResponseDigest,
                response_guard_reason_codes = :responseGuardReasonCodes,
                updated_at = :updatedAt
            where execution_id = :executionId
            """)
            .param("executionId", executionId)
            .param("responseGuardStatus", responseGuardResult.status())
            .param("providerResponseDigest", connectorResult.responseDigest())
            .param("responseGuardReasonCodes", String.join(",", responseGuardResult.reasonCodes()))
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
    public void recordAuthorization(String executionId, String authorizationStatus) {
        jdbcClient.sql("""
            update runtime.runtime_execution
            set authorization_status = :authorizationStatus,
                updated_at = :updatedAt
            where execution_id = :executionId
            """)
            .param("executionId", executionId)
            .param("authorizationStatus", authorizationStatus)
            .param("updatedAt", OffsetDateTime.now(clock))
            .update();
    }

    @Override
    public void recordControlledDelivery(String executionId, ControlledDeliveryResult result) {
        OffsetDateTime recordedAt = OffsetDateTime.now(clock);
        jdbcClient.sql("""
            update runtime.runtime_execution
            set controlled_delivery_status = :deliveryStatus,
                controlled_delivery_response_digest = :responseDigest,
                controlled_delivery_reason_code = :reasonCode,
                controlled_delivered_at = :deliveredAt,
                updated_at = :updatedAt
            where execution_id = :executionId
            """)
            .param("executionId", executionId)
            .param("deliveryStatus", result.deliveryStatus())
            .param("responseDigest", result.responseDigest())
            .param("reasonCode", result.reasonCode())
            .param("deliveredAt", result.isDelivered() ? recordedAt : null)
            .param("updatedAt", recordedAt)
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
                   purpose_code, subject_ref_digest, provider_profile_id,
                   destination_profile_id, destination_profile_version, destination_profile_digest,
                   destination_tenant_id, destination_region, destination_retention_policy,
                   destination_training_use_allowed,
                   institution_id, approval_reference, approval_version, approval_scope_digest,
                   approval_reuse_status,
                   (select reason_codes from runtime.policy_harness_binding phb
                    where phb.execution_id = runtime_execution.execution_id) as approval_reason_codes,
                   (select policy_layers from runtime.policy_harness_binding phb
                    where phb.execution_id = runtime_execution.execution_id) as policy_layers,
                   policy_layers_digest,
                   input_digest,
                   canonical_context_digest, runtime_context_digest,
                   policy_version, snapshot_digest, decision_id, final_action,
                   transform_execution_id, transform_status, transform_output_digest,
                   outbound_payload_id, outbound_candidate_digest, outbound_guard_status,
                   connector_execution_id, connector_status, response_guard_status,
                   response_guard_reason_codes,
                   (select requested_fields from runtime.policy_harness_binding phb
                    where phb.execution_id = runtime_execution.execution_id) as requested_fields,
                   requested_fields_digest, requested_field_count,
                   (select retrieved_fields from runtime.policy_harness_binding phb
                    where phb.execution_id = runtime_execution.execution_id) as retrieved_fields,
                   retrieved_fields_digest, retrieved_field_count,
                   (select transformed_fields from runtime.policy_harness_binding phb
                    where phb.execution_id = runtime_execution.execution_id) as transformed_fields,
                   transformed_fields_digest, transformed_field_count,
                   (select released_fields from runtime.policy_harness_binding phb
                    where phb.execution_id = runtime_execution.execution_id) as released_fields,
                   released_fields_digest, released_field_count,
                   provider_request_id, provider_request_digest, provider_response_digest,
                   authorization_status,
                   controlled_delivery_status, controlled_delivery_response_digest,
                   controlled_delivery_reason_code, controlled_delivered_at,
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

    private String policyLayerAuditValue(PolicyLayerReference layer) {
        return String.join(":", layer.layer(), layer.referenceId(), layer.version(), layer.digest());
    }

    private String auditFields(java.util.Set<String> fields) {
        return fields.stream().sorted().collect(Collectors.joining(","));
    }
}
