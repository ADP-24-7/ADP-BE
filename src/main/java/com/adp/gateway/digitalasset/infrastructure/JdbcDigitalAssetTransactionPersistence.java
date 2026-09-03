package com.adp.gateway.digitalasset.infrastructure;

import java.sql.Types;
import java.time.Clock;
import java.time.OffsetDateTime;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class JdbcDigitalAssetTransactionPersistence {
    private final JdbcClient jdbcClient;
    private final Clock clock;

    public JdbcDigitalAssetTransactionPersistence(JdbcClient jdbcClient, Clock clock) {
        this.jdbcClient = jdbcClient;
        this.clock = clock;
    }

    public void record(
        String executionId,
        String externalRequestId,
        String externalTransactionId,
        String settlementId,
        String settlementStatus,
        String reconciliationResult,
        String providerResponseDigest
    ) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        jdbcClient.sql("""
            insert into runtime.digital_asset_transaction (
                execution_id, external_request_id, external_transaction_id, settlement_id,
                settlement_status, reconciliation_result, provider_response_digest, created_at, updated_at
            ) values (
                :executionId, :externalRequestId, :externalTransactionId, :settlementId,
                :settlementStatus, :reconciliationResult, :providerResponseDigest, :now, :now
            )
            on conflict (execution_id) do update set
                external_transaction_id = excluded.external_transaction_id,
                settlement_id = excluded.settlement_id,
                settlement_status = excluded.settlement_status,
                reconciliation_result = excluded.reconciliation_result,
                provider_response_digest = excluded.provider_response_digest,
                updated_at = excluded.updated_at
            """)
            .param("executionId", executionId)
            .param("externalRequestId", externalRequestId)
            .param("externalTransactionId", externalTransactionId, Types.VARCHAR)
            .param("settlementId", settlementId, Types.VARCHAR)
            .param("settlementStatus", settlementStatus)
            .param("reconciliationResult", reconciliationResult)
            .param("providerResponseDigest", providerResponseDigest, Types.VARCHAR)
            .param("now", now)
            .update();
    }
}
