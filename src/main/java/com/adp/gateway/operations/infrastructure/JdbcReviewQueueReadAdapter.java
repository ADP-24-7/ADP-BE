package com.adp.gateway.operations.infrastructure;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import com.adp.gateway.egress.domain.ExecutionPackType;
import com.adp.gateway.operations.application.ReviewQueueReadPort;
import com.adp.gateway.operations.domain.ReviewNextAction;
import com.adp.gateway.operations.domain.ReviewQueueDetail;
import com.adp.gateway.operations.domain.ReviewQueueItem;
import com.adp.gateway.operations.domain.ReviewQueuePage;
import com.adp.gateway.operations.domain.ReviewSource;
import com.adp.gateway.runtime.application.RuntimeExecutionNotFoundException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class JdbcReviewQueueReadAdapter implements ReviewQueueReadPort {
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() { };

    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;

    public JdbcReviewQueueReadAdapter(JdbcClient jdbcClient, ObjectMapper objectMapper) {
        this.jdbcClient = jdbcClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public ReviewQueuePage search(
        String institutionId,
        Set<String> allowedWorkloads,
        ExecutionPackType executionPack,
        String workloadId,
        int page,
        int size
    ) {
        StringBuilder where = new StringBuilder(" where re.institution_id = :institutionId")
            .append(" and re.status = 'REVIEW_REQUIRED'");
        appendWorkloadScope(where, allowedWorkloads);
        if (executionPack != null) where.append(" and re.execution_pack = :executionPack");
        if (workloadId != null) where.append(" and re.workload_id = :workloadId");
        String from = fromClause();
        JdbcClient.StatementSpec select = bind(jdbcClient.sql("""
            select re.execution_id, re.request_id, re.trace_id, re.institution_id,
                   coalesce(re.execution_pack, 'COMMON') as execution_pack,
                   re.workload_id, re.purpose_code, re.status as runtime_status, re.final_action,
                   pe.reason_codes as policy_reason_codes, rd.reason_codes as decision_reason_codes,
                   rr.recovery_id, rr.recovery_status, rr.last_error_code,
                   dape.status as post_execution_evidence_status,
                   re.created_at, re.updated_at
            """ + from + where + " order by re.updated_at desc, re.execution_id desc limit :size offset :offset"),
            institutionId, allowedWorkloads, executionPack, workloadId)
            .param("size", size)
            .param("offset", page * size);
        List<ReviewQueueItem> items = select.query((rs, rowNum) -> {
            ReviewSource source = source(
                rs.getString("recovery_status"), rs.getString("post_execution_evidence_status")
            );
            return new ReviewQueueItem(
                rs.getString("execution_id"), rs.getString("request_id"), rs.getString("trace_id"),
                rs.getString("institution_id"), ExecutionPackType.valueOf(rs.getString("execution_pack")),
                rs.getString("workload_id"), rs.getString("purpose_code"), rs.getString("runtime_status"),
                rs.getString("final_action"), source, nextAction(source),
                reasonCodes(rs.getString("policy_reason_codes"), rs.getString("decision_reason_codes"),
                    rs.getString("last_error_code")),
                rs.getString("recovery_id"), rs.getString("recovery_status"),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class)
            );
        }).list();
        long total = bind(
            jdbcClient.sql("select count(*) " + from + where),
            institutionId, allowedWorkloads, executionPack, workloadId
        ).query(Long.class).single();
        return new ReviewQueuePage(items, page, size, total);
    }

    @Override
    public ReviewQueueDetail load(String executionId, String institutionId, Set<String> allowedWorkloads) {
        StringBuilder scope = new StringBuilder(" and re.institution_id = :institutionId")
            .append(" and re.status = 'REVIEW_REQUIRED'");
        appendWorkloadScope(scope, allowedWorkloads);
        JdbcClient.StatementSpec statement = jdbcClient.sql("""
            select re.execution_id, re.request_id, re.trace_id, re.institution_id,
                   coalesce(re.execution_pack, 'COMMON') as execution_pack,
                   re.workload_id, re.purpose_code, re.status as runtime_status, re.final_action,
                   pe.reason_codes as policy_reason_codes, rd.reason_codes as decision_reason_codes,
                   pe.profile_id, pe.profile_version, pe.profile_digest,
                   re.connector_status, re.response_guard_status, re.controlled_delivery_status,
                   rr.recovery_id, rr.recovery_status, rr.retry_disposition, rr.last_error_code,
                   dape.status as post_execution_evidence_status,
                   coalesce(dape.mismatch_fields::text, '[]') as mismatch_fields,
                   re.created_at, re.updated_at
            """ + fromClause() + " where re.execution_id = :executionId" + scope)
            .param("executionId", executionId)
            .param("institutionId", institutionId);
        statement = bindWorkloads(statement, allowedWorkloads);
        return statement.query((rs, rowNum) -> {
            ReviewSource source = source(
                rs.getString("recovery_status"), rs.getString("post_execution_evidence_status")
            );
            return new ReviewQueueDetail(
                rs.getString("execution_id"), rs.getString("request_id"), rs.getString("trace_id"),
                rs.getString("institution_id"), ExecutionPackType.valueOf(rs.getString("execution_pack")),
                rs.getString("workload_id"), rs.getString("purpose_code"), rs.getString("runtime_status"),
                rs.getString("final_action"), source, nextAction(source),
                reasonCodes(rs.getString("policy_reason_codes"), rs.getString("decision_reason_codes"),
                    rs.getString("last_error_code")),
                rs.getString("profile_id"), rs.getString("profile_version"), rs.getString("profile_digest"),
                rs.getString("connector_status"), rs.getString("response_guard_status"),
                rs.getString("controlled_delivery_status"), rs.getString("recovery_id"),
                rs.getString("recovery_status"), rs.getString("retry_disposition"),
                rs.getString("last_error_code"), rs.getString("post_execution_evidence_status"),
                jsonList(rs.getString("mismatch_fields")),
                "/v1/runtime/executions/" + executionId + "/trace",
                "/api/admin/audit/executions/" + executionId + "/evidence",
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class)
            );
        }).optional().orElseThrow(() -> new RuntimeExecutionNotFoundException(executionId));
    }

    private String fromClause() {
        return """
            from runtime.runtime_execution re
            left join runtime.execution_pack_policy_evaluation pe on pe.execution_id = re.execution_id
            left join runtime.runtime_decision rd on rd.execution_id = re.execution_id
            left join runtime.external_interaction_recovery rr on rr.execution_id = re.execution_id
            left join runtime.digital_asset_post_execution_evidence dape on dape.execution_id = re.execution_id
            """;
    }

    private JdbcClient.StatementSpec bind(
        JdbcClient.StatementSpec statement,
        String institutionId,
        Set<String> allowedWorkloads,
        ExecutionPackType executionPack,
        String workloadId
    ) {
        statement = statement.param("institutionId", institutionId);
        statement = bindWorkloads(statement, allowedWorkloads);
        if (executionPack != null) statement = statement.param("executionPack", executionPack.name());
        if (workloadId != null) statement = statement.param("workloadId", workloadId);
        return statement;
    }

    private void appendWorkloadScope(StringBuilder sql, Set<String> allowedWorkloads) {
        if (allowedWorkloads.contains("*")) return;
        sql.append(allowedWorkloads.isEmpty() ? " and 1 = 0" : " and re.workload_id in (:allowedWorkloads)");
    }

    private JdbcClient.StatementSpec bindWorkloads(
        JdbcClient.StatementSpec statement,
        Set<String> allowedWorkloads
    ) {
        if (!allowedWorkloads.contains("*") && !allowedWorkloads.isEmpty()) {
            return statement.param("allowedWorkloads", allowedWorkloads);
        }
        return statement;
    }

    private ReviewSource source(String recoveryStatus, String postExecutionStatus) {
        if ("MANUAL_REVIEW".equals(recoveryStatus) || "EXHAUSTED".equals(recoveryStatus)) {
            return ReviewSource.RECOVERY;
        }
        if ("REVIEW_REQUIRED".equals(postExecutionStatus)) return ReviewSource.POST_EXECUTION;
        return ReviewSource.POLICY;
    }

    private ReviewNextAction nextAction(ReviewSource source) {
        return switch (source) {
            case RECOVERY -> ReviewNextAction.RECONCILE_EXTERNAL_STATUS;
            case POST_EXECUTION -> ReviewNextAction.INSPECT_POST_EXECUTION_EVIDENCE;
            case POLICY -> ReviewNextAction.INSPECT_TRACE;
        };
    }

    private List<String> reasonCodes(String policyReasons, String decisionReasons, String recoveryError) {
        String selected = firstNonBlank(policyReasons, decisionReasons, recoveryError);
        if (selected == null) return List.of();
        return Arrays.stream(selected.split(","))
            .map(String::trim)
            .filter(value -> !value.isBlank())
            .distinct()
            .toList();
    }

    private String firstNonBlank(String... values) {
        return Arrays.stream(values).filter(value -> value != null && !value.isBlank()).findFirst().orElse(null);
    }

    private List<String> jsonList(String value) {
        try {
            return objectMapper.readValue(value, STRING_LIST);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to read review queue mismatch fields", exception);
        }
    }
}
