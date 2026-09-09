package com.adp.gateway.auth.infrastructure;

import com.adp.gateway.auth.application.DeniedRequestAttemptPort;
import com.adp.gateway.auth.domain.DeniedRequestAttempt;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class JdbcDeniedRequestAttemptAdapter implements DeniedRequestAttemptPort {

    private final JdbcClient jdbcClient;

    public JdbcDeniedRequestAttemptAdapter(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public void record(DeniedRequestAttempt attempt) {
        jdbcClient.sql("""
            insert into runtime.request_attempt (
                attempt_id, request_id, trace_id, client_trace_id_digest, principal_id,
                institution_id, workload_id, purpose_code, subject_ref_digest,
                authorization_result, reason_code, created_at
            ) values (
                :attemptId, :requestId, :traceId, :clientTraceIdDigest, :principalId,
                :institutionId, :workloadId, :purposeCode, :subjectRefDigest,
                'DENIED', :reasonCode, :createdAt
            )
            """)
            .param("attemptId", attempt.attemptId())
            .param("requestId", attempt.requestId())
            .param("traceId", attempt.traceId())
            .param("clientTraceIdDigest", attempt.clientTraceIdDigest())
            .param("principalId", attempt.principalId())
            .param("institutionId", attempt.institutionId())
            .param("workloadId", attempt.workloadId())
            .param("purposeCode", attempt.purposeCode())
            .param("subjectRefDigest", attempt.subjectRefDigest())
            .param("reasonCode", attempt.reasonCode())
            .param("createdAt", attempt.createdAt())
            .update();
    }
}
