package com.adp.gateway.audit.infrastructure;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

import com.adp.gateway.audit.application.AuditReadPort;
import com.adp.gateway.audit.domain.AuditExecutionPage;
import com.adp.gateway.audit.domain.AuditExecutionSummary;
import com.adp.gateway.audit.domain.ExecutionEvidencePack;
import com.adp.gateway.runtime.application.RuntimeExecutionNotFoundException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class JdbcAuditReadAdapter implements AuditReadPort {
    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;

    public JdbcAuditReadAdapter(JdbcClient jdbcClient, ObjectMapper objectMapper) {
        this.jdbcClient = jdbcClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public AuditExecutionPage search(
        String institutionId,
        Set<String> allowedWorkloads,
        String workloadId,
        String status,
        OffsetDateTime from,
        OffsetDateTime to,
        int page,
        int size
    ) {
        StringBuilder where = new StringBuilder(" where re.institution_id = :institutionId");
        appendWorkloadScope(where, allowedWorkloads);
        if (workloadId != null) where.append(" and re.workload_id = :workloadId");
        if (status != null) where.append(" and re.status = :status");
        if (from != null) where.append(" and re.created_at >= :from");
        if (to != null) where.append(" and re.created_at <= :to");

        String select = """
            select re.execution_id, re.request_id, re.trace_id, re.institution_id,
                   re.workload_id, re.purpose_code, re.status, re.final_action,
                   re.policy_version, re.snapshot_digest, re.destination_profile_id,
                   re.destination_profile_version, re.connector_status,
                   rr.recovery_status, re.created_at, re.updated_at
            from runtime.runtime_execution re
            left join runtime.external_interaction_recovery rr on rr.execution_id = re.execution_id
            """ + where + " order by re.created_at desc, re.execution_id desc limit :size offset :offset";
        String count = "select count(*) from runtime.runtime_execution re" + where;

        JdbcClient.StatementSpec selectSpec = bind(
            jdbcClient.sql(select), institutionId, allowedWorkloads, workloadId, status, from, to
        )
            .param("size", size).param("offset", page * size);
        List<AuditExecutionSummary> items = selectSpec.query(AuditExecutionSummary.class).list();
        long total = bind(
            jdbcClient.sql(count), institutionId, allowedWorkloads, workloadId, status, from, to
        )
            .query(Long.class).single();
        return new AuditExecutionPage(items, page, size, total);
    }

    @Override
    public ExecutionEvidencePack loadEvidence(
        String executionId,
        String institutionId,
        Set<String> allowedWorkloads
    ) {
        StringBuilder scope = new StringBuilder(" and re.institution_id = :institutionId");
        appendWorkloadScope(scope, allowedWorkloads);
        JdbcClient.StatementSpec statement = jdbcClient.sql("""
            select re.execution_id, re.request_id, re.trace_id, re.institution_id,
                   re.workload_id, re.purpose_code, re.status as runtime_status,
                   re.authorization_status, re.approval_reference, re.approval_version,
                   re.approval_scope_digest, re.policy_version, re.snapshot_digest,
                   re.decision_id, re.final_action, re.subject_ref_digest, re.input_digest,
                   re.canonical_context_digest, re.runtime_context_digest,
                   re.requested_field_count, re.requested_fields_digest,
                   re.retrieved_field_count, re.retrieved_fields_digest,
                   re.transformed_field_count, re.transformed_fields_digest,
                   re.released_field_count, re.released_fields_digest,
                   re.destination_profile_id, re.destination_profile_version,
                   re.destination_profile_digest, re.outbound_candidate_digest,
                   re.outbound_guard_status, re.connector_execution_id, re.connector_status,
                   re.provider_request_digest, re.provider_response_digest,
                   re.response_guard_status, re.controlled_delivery_status,
                   re.controlled_delivery_response_digest,
                   rr.recovery_status, rr.retry_disposition, rr.attempt_count, rr.max_attempts,
                   rr.last_observed_external_status, rr.last_status_queried_at,
                   rr.status_query_evidence_digest, rr.last_error_code,
                   ae.audit_id, ae.reason_code, ae.evidence_refs,
                   re.created_at, re.updated_at
            from runtime.runtime_execution re
            left join runtime.external_interaction_recovery rr on rr.execution_id = re.execution_id
            left join lateral (
                select audit_id, reason_code, evidence_refs
                from audit_event where decision_id = re.decision_id
                order by created_at desc limit 1
            ) ae on true
            where re.execution_id = :executionId
            """ + scope)
            .param("executionId", executionId)
            .param("institutionId", institutionId);
        statement = bindWorkloadScope(statement, allowedWorkloads);
        EvidenceRow row = statement.query(EvidenceRow.class)
            .optional()
            .orElseThrow(() -> new RuntimeExecutionNotFoundException(executionId));
        ExecutionEvidencePack unsigned = row.toEvidence(null);
        return row.toEvidence(digest(unsigned));
    }

    private JdbcClient.StatementSpec bind(
        JdbcClient.StatementSpec spec,
        String institutionId,
        Set<String> allowedWorkloads,
        String workloadId,
        String status,
        OffsetDateTime from,
        OffsetDateTime to
    ) {
        spec = spec.param("institutionId", institutionId);
        spec = bindWorkloadScope(spec, allowedWorkloads);
        if (workloadId != null) spec = spec.param("workloadId", workloadId);
        if (status != null) spec = spec.param("status", status);
        if (from != null) spec = spec.param("from", from);
        if (to != null) spec = spec.param("to", to);
        return spec;
    }

    private void appendWorkloadScope(StringBuilder sql, Set<String> allowedWorkloads) {
        if (allowedWorkloads.contains("*")) {
            return;
        }
        sql.append(allowedWorkloads.isEmpty()
            ? " and 1 = 0"
            : " and re.workload_id in (:allowedWorkloads)");
    }

    private JdbcClient.StatementSpec bindWorkloadScope(
        JdbcClient.StatementSpec spec,
        Set<String> allowedWorkloads
    ) {
        if (!allowedWorkloads.contains("*") && !allowedWorkloads.isEmpty()) {
            return spec.param("allowedWorkloads", allowedWorkloads);
        }
        return spec;
    }

    private String digest(ExecutionEvidencePack evidence) {
        try {
            byte[] canonical = objectMapper.writeValueAsBytes(evidence);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical));
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Unable to digest evidence pack", exception);
        }
    }

    private static List<String> values(String value) {
        if (value == null || value.isBlank()) return List.of();
        return Arrays.stream(value.split(",")).map(String::trim).filter(item -> !item.isBlank()).toList();
    }

    private record EvidenceRow(
        String executionId, String requestId, String traceId, String institutionId,
        String workloadId, String purposeCode, String runtimeStatus, String authorizationStatus,
        String approvalReference, String approvalVersion, String approvalScopeDigest,
        String policyVersion, String snapshotDigest, String decisionId, String finalAction,
        String subjectRefDigest, String inputDigest, String canonicalContextDigest, String runtimeContextDigest,
        Integer requestedFieldCount, String requestedFieldsDigest,
        Integer retrievedFieldCount, String retrievedFieldsDigest,
        Integer transformedFieldCount, String transformedFieldsDigest,
        Integer releasedFieldCount, String releasedFieldsDigest,
        String destinationProfileId, String destinationProfileVersion, String destinationProfileDigest,
        String outboundCandidateDigest, String outboundGuardStatus, String connectorExecutionId,
        String connectorStatus, String providerRequestDigest, String providerResponseDigest,
        String responseGuardStatus, String controlledDeliveryStatus, String controlledDeliveryResponseDigest,
        String recoveryStatus, String retryDisposition, Integer attemptCount, Integer maxAttempts,
        String lastObservedExternalStatus, OffsetDateTime lastStatusQueriedAt,
        String statusQueryEvidenceDigest, String lastErrorCode,
        String auditId, String reasonCode, String evidenceRefs,
        OffsetDateTime createdAt, OffsetDateTime updatedAt
    ) {
        ExecutionEvidencePack toEvidence(String exportContentDigest) {
            return new ExecutionEvidencePack(
                "adp-execution-evidence/v1", exportContentDigest, executionId, requestId, traceId,
                institutionId, workloadId, purposeCode, runtimeStatus, authorizationStatus,
                new ExecutionEvidencePack.PolicyEvidence(
                    approvalReference, approvalVersion, approvalScopeDigest, policyVersion,
                    snapshotDigest, decisionId, finalAction
                ),
                new ExecutionEvidencePack.DataEvidence(
                    subjectRefDigest, inputDigest, canonicalContextDigest, runtimeContextDigest,
                    requestedFieldCount, requestedFieldsDigest, retrievedFieldCount, retrievedFieldsDigest,
                    transformedFieldCount, transformedFieldsDigest, releasedFieldCount, releasedFieldsDigest
                ),
                new ExecutionEvidencePack.EgressEvidence(
                    destinationProfileId, destinationProfileVersion, destinationProfileDigest,
                    outboundCandidateDigest, outboundGuardStatus, connectorExecutionId, connectorStatus,
                    providerRequestDigest, providerResponseDigest, responseGuardStatus,
                    controlledDeliveryStatus, controlledDeliveryResponseDigest
                ),
                new ExecutionEvidencePack.RecoveryEvidence(
                    recoveryStatus, retryDisposition, attemptCount, maxAttempts,
                    lastObservedExternalStatus, lastStatusQueriedAt, statusQueryEvidenceDigest, lastErrorCode
                ),
                new ExecutionEvidencePack.AuditEvidence(auditId, reasonCode, values(evidenceRefs)),
                createdAt, updatedAt
            );
        }
    }
}
