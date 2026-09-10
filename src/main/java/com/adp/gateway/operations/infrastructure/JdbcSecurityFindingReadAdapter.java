package com.adp.gateway.operations.infrastructure;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

import com.adp.gateway.egress.domain.ExecutionPackType;
import com.adp.gateway.operations.application.SecurityFindingNotFoundException;
import com.adp.gateway.operations.application.SecurityFindingReadPort;
import com.adp.gateway.operations.domain.SecurityFindingDetail;
import com.adp.gateway.operations.domain.SecurityFindingItem;
import com.adp.gateway.operations.domain.SecurityFindingPage;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class JdbcSecurityFindingReadAdapter implements SecurityFindingReadPort {
    private final JdbcClient jdbcClient;

    public JdbcSecurityFindingReadAdapter(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public SecurityFindingPage search(
        String institutionId,
        Set<String> allowedWorkloads,
        ExecutionPackType executionPack,
        String workloadId,
        String findingType,
        OffsetDateTime from,
        OffsetDateTime to,
        int page,
        int size
    ) {
        StringBuilder where = new StringBuilder(" where re.institution_id = :institutionId");
        appendWorkloadScope(where, allowedWorkloads);
        if (executionPack != null) where.append(" and re.execution_pack = :executionPack");
        if (workloadId != null) where.append(" and re.workload_id = :workloadId");
        if (findingType != null) where.append(" and sf.finding_type = :findingType");
        if (from != null) where.append(" and sf.created_at >= :from");
        if (to != null) where.append(" and sf.created_at <= :to");

        JdbcClient.StatementSpec select = bind(jdbcClient.sql("""
            select sf.id, sf.execution_id, re.request_id, re.trace_id, re.institution_id,
                   re.execution_pack, re.workload_id, re.purpose_code,
                   sf.finding_type, sf.location, sf.detector_version, sf.evidence_digest, sf.created_at
            from runtime.response_sensitive_finding sf
            join runtime.runtime_execution re on re.execution_id = sf.execution_id
            """ + where + " order by sf.created_at desc, sf.id desc limit :size offset :offset"),
            institutionId, allowedWorkloads, executionPack, workloadId, findingType, from, to)
            .param("size", size)
            .param("offset", page * size);
        List<SecurityFindingItem> items = select.query((rs, rowNum) -> new SecurityFindingItem(
            rs.getLong("id"), rs.getString("execution_id"), rs.getString("request_id"),
            rs.getString("trace_id"), rs.getString("institution_id"),
            ExecutionPackType.valueOf(rs.getString("execution_pack")), rs.getString("workload_id"),
            rs.getString("purpose_code"), rs.getString("finding_type"), rs.getString("location"),
            rs.getString("detector_version"), rs.getString("evidence_digest"),
            rs.getObject("created_at", OffsetDateTime.class)
        )).list();
        long total = bind(jdbcClient.sql("""
            select count(*)
            from runtime.response_sensitive_finding sf
            join runtime.runtime_execution re on re.execution_id = sf.execution_id
            """ + where), institutionId, allowedWorkloads, executionPack, workloadId, findingType, from, to)
            .query(Long.class).single();
        return new SecurityFindingPage(items, page, size, total);
    }

    @Override
    public SecurityFindingDetail load(long findingId, String institutionId, Set<String> allowedWorkloads) {
        StringBuilder scope = new StringBuilder(" and re.institution_id = :institutionId");
        appendWorkloadScope(scope, allowedWorkloads);
        JdbcClient.StatementSpec statement = jdbcClient.sql("""
            select sf.id, sf.execution_id, re.request_id, re.trace_id, re.institution_id,
                   re.execution_pack, re.workload_id, re.purpose_code, re.status as runtime_status,
                   sf.finding_type, sf.location, sf.start_offset, sf.end_offset,
                   sf.detector_version, sf.evidence_digest, sf.connector_execution_id,
                   rg.connector_status, rg.status as response_guard_status, rg.response_digest,
                   sf.created_at
            from runtime.response_sensitive_finding sf
            join runtime.runtime_execution re on re.execution_id = sf.execution_id
            join runtime.response_guard_result rg
              on rg.execution_id = sf.execution_id
             and rg.connector_execution_id = sf.connector_execution_id
            where sf.id = :findingId
            """ + scope)
            .param("findingId", findingId)
            .param("institutionId", institutionId);
        statement = bindWorkloads(statement, allowedWorkloads);
        return statement.query((rs, rowNum) -> {
            String executionId = rs.getString("execution_id");
            return new SecurityFindingDetail(
                rs.getLong("id"), executionId, rs.getString("request_id"), rs.getString("trace_id"),
                rs.getString("institution_id"), ExecutionPackType.valueOf(rs.getString("execution_pack")),
                rs.getString("workload_id"), rs.getString("purpose_code"), rs.getString("runtime_status"),
                rs.getString("finding_type"), rs.getString("location"), rs.getInt("start_offset"),
                rs.getInt("end_offset"), rs.getString("detector_version"), rs.getString("evidence_digest"),
                rs.getString("connector_execution_id"), rs.getString("connector_status"),
                rs.getString("response_guard_status"), rs.getString("response_digest"),
                "/v1/runtime/executions/" + executionId + "/trace",
                "/api/admin/audit/executions/" + executionId + "/evidence",
                rs.getObject("created_at", OffsetDateTime.class)
            );
        }).optional().orElseThrow(() -> new SecurityFindingNotFoundException(findingId));
    }

    private JdbcClient.StatementSpec bind(
        JdbcClient.StatementSpec statement,
        String institutionId,
        Set<String> allowedWorkloads,
        ExecutionPackType executionPack,
        String workloadId,
        String findingType,
        OffsetDateTime from,
        OffsetDateTime to
    ) {
        statement = statement.param("institutionId", institutionId);
        statement = bindWorkloads(statement, allowedWorkloads);
        if (executionPack != null) statement = statement.param("executionPack", executionPack.name());
        if (workloadId != null) statement = statement.param("workloadId", workloadId);
        if (findingType != null) statement = statement.param("findingType", findingType);
        if (from != null) statement = statement.param("from", from);
        if (to != null) statement = statement.param("to", to);
        return statement;
    }

    private void appendWorkloadScope(StringBuilder sql, Set<String> allowedWorkloads) {
        if (allowedWorkloads.contains("*")) return;
        sql.append(allowedWorkloads.isEmpty() ? " and 1 = 0" : " and re.workload_id in (:allowedWorkloads)");
    }

    private JdbcClient.StatementSpec bindWorkloads(JdbcClient.StatementSpec statement, Set<String> allowedWorkloads) {
        if (!allowedWorkloads.contains("*") && !allowedWorkloads.isEmpty()) {
            return statement.param("allowedWorkloads", allowedWorkloads);
        }
        return statement;
    }
}
