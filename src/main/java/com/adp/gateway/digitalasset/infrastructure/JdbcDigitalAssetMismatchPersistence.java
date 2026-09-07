package com.adp.gateway.digitalasset.infrastructure;

import java.time.Clock;
import java.time.OffsetDateTime;

import com.adp.gateway.digitalasset.application.DigitalAssetMismatchPersistencePort;
import com.adp.gateway.digitalasset.domain.DigitalAssetReconciliationAssessment;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class JdbcDigitalAssetMismatchPersistence implements DigitalAssetMismatchPersistencePort {
    private final JdbcClient jdbcClient;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    public JdbcDigitalAssetMismatchPersistence(JdbcClient jdbcClient, Clock clock, ObjectMapper objectMapper) {
        this.jdbcClient = jdbcClient;
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    @Override
    public void open(String executionId, DigitalAssetReconciliationAssessment assessment) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        jdbcClient.sql("""
            insert into runtime.digital_asset_mismatch_case (
                execution_id, severity, mismatched_fields, expected_payload_digest,
                actual_payload_digest, case_status, auto_retry_allowed, opened_at, updated_at
            ) values (
                :executionId, :severity, cast(:mismatchedFields as jsonb), :expectedDigest,
                :actualDigest, 'OPEN', false, :now, :now
            )
            """)
            .param("executionId", executionId)
            .param("severity", assessment.result())
            .param("mismatchedFields", toJson(assessment.mismatchedFields()))
            .param("expectedDigest", assessment.expectedDigest())
            .param("actualDigest", assessment.actualDigest())
            .param("now", now)
            .update();
    }

    private String toJson(java.util.List<String> fields) {
        try {
            return objectMapper.writeValueAsString(fields);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Mismatch fields could not be serialized", exception);
        }
    }
}
