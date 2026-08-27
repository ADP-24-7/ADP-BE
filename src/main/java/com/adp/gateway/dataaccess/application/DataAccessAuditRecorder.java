package com.adp.gateway.dataaccess.application;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.stream.Collectors;

import com.adp.gateway.retrieval.domain.RetrievalResult;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class DataAccessAuditRecorder {

    private final JdbcClient jdbcClient;
    private final Clock clock;

    public DataAccessAuditRecorder(JdbcClient jdbcClient, Clock clock) {
        this.jdbcClient = jdbcClient;
        this.clock = clock;
    }

    public String record(DataAccessRequest request, RetrievalResult result) {
        String dataAccessId = "da_" + UUID.randomUUID();
        String selectedFields = result.selectedFields().stream()
            .map(field -> field.qualifiedName() + ":" + field.dataClass().name())
            .sorted()
            .collect(Collectors.joining(","));

        jdbcClient.sql("""
                insert into data_access_event (
                    data_access_id, request_id, trace_id, workload_id, purpose,
                    subject_type, subject_id, profile_id, selected_fields, row_count, created_at
                ) values (
                    :dataAccessId, :requestId, :traceId, :workloadId, :purpose,
                    :subjectType, :subjectId, :profileId, :selectedFields, :rowCount, :createdAt
                )
                """)
            .param("dataAccessId", dataAccessId)
            .param("requestId", request.requestId())
            .param("traceId", request.traceId())
            .param("workloadId", request.workloadId())
            .param("purpose", request.purpose())
            .param("subjectType", request.subject().subjectType())
            .param("subjectId", request.subject().subjectId())
            .param("profileId", result.profileId())
            .param("selectedFields", selectedFields)
            .param("rowCount", result.rowCount())
            .param("createdAt", OffsetDateTime.now(clock))
            .update();

        return dataAccessId;
    }
}
