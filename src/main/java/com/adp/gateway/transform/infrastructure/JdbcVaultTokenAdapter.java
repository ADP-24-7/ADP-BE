package com.adp.gateway.transform.infrastructure;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.adp.gateway.transform.application.VaultTokenPort;
import com.adp.gateway.transform.application.VaultTokenRequest;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class JdbcVaultTokenAdapter implements VaultTokenPort {

    private final JdbcClient jdbcClient;
    private final Clock clock;
    private final MeterRegistry meterRegistry;
    private final TransactionTemplate transactionTemplate;

    public JdbcVaultTokenAdapter(
        JdbcClient jdbcClient,
        Clock clock,
        MeterRegistry meterRegistry,
        PlatformTransactionManager transactionManager
    ) {
        this.jdbcClient = jdbcClient;
        this.clock = clock;
        this.meterRegistry = meterRegistry;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public String tokenFor(VaultTokenRequest request) {
        Timer.Sample timer = Timer.start(meterRegistry);
        try {
            return find(request)
                .orElseGet(() -> create(request));
        } catch (RuntimeException exception) {
            meterRegistry.counter("vault.token.failure.total", "result", "FAILED").increment();
            throw exception;
        } finally {
            timer.stop(Timer.builder("vault.token.duration")
                .description("Vault token lookup/create duration")
                .register(meterRegistry));
        }
    }

    private java.util.Optional<String> find(VaultTokenRequest request) {
        java.util.Optional<String> token = jdbcClient.sql("""
                select token_ref
                from vault.token_mapping
                where mapping_scope = :mappingScope
                  and data_class = :dataClass
                  and source_value_digest = :sourceValueDigest
                  and key_version = :keyVersion
                  and mapping_version = :mappingVersion
                  and status = 'ACTIVE'
                  and (expires_at is null or expires_at > :now)
                """)
            .param("mappingScope", request.mappingScope())
            .param("dataClass", request.dataClass().name())
            .param("sourceValueDigest", request.sourceValueDigest())
            .param("keyVersion", request.keyVersion())
            .param("mappingVersion", request.mappingVersion())
            .param("now", OffsetDateTime.now(clock))
            .query(String.class)
            .optional();
        meterRegistry.counter(
            "vault.token.lookup.total",
            "result", token.isPresent() ? "HIT" : "MISS"
        ).increment();
        return token;
    }

    private String create(VaultTokenRequest request) {
        String tokenRef = "vault_tok_" + UUID.randomUUID();
        try {
            transactionTemplate.execute(status -> {
                expireStaleMappings(request, tokenRef);
                insertActiveMapping(request, tokenRef);
                return tokenRef;
            });
            meterRegistry.counter("vault.token.create.total", "result", "CREATED").increment();
            return tokenRef;
        } catch (DuplicateKeyException exception) {
            meterRegistry.counter("vault.token.create.total", "result", "DUPLICATE").increment();
            return find(request)
                .orElseThrow(() -> exception);
        }
    }

    private void insertActiveMapping(VaultTokenRequest request, String tokenRef) {
        jdbcClient.sql("""
                insert into vault.token_mapping (
                    token_ref, mapping_scope, data_class, source_value_digest, key_version,
                    mapping_version, status, expires_at, created_at
                )
                values (
                    :tokenRef, :mappingScope, :dataClass, :sourceValueDigest, :keyVersion,
                    :mappingVersion, 'ACTIVE', :expiresAt, :createdAt
                )
                """)
            .param("tokenRef", tokenRef)
            .param("mappingScope", request.mappingScope())
            .param("dataClass", request.dataClass().name())
            .param("sourceValueDigest", request.sourceValueDigest())
            .param("keyVersion", request.keyVersion())
            .param("mappingVersion", request.mappingVersion())
            .param("expiresAt", expiresAt(request))
            .param("createdAt", OffsetDateTime.now(clock))
            .update();
    }

    private void expireStaleMappings(VaultTokenRequest request, String replacementTokenRef) {
        jdbcClient.sql("""
                update vault.token_mapping
                set status = 'EXPIRED',
                    replaced_by_token_ref = :replacementTokenRef
                where mapping_scope = :mappingScope
                  and data_class = :dataClass
                  and source_value_digest = :sourceValueDigest
                  and key_version = :keyVersion
                  and mapping_version = :mappingVersion
                  and status = 'ACTIVE'
                  and expires_at <= :now
                """)
            .param("replacementTokenRef", replacementTokenRef)
            .param("mappingScope", request.mappingScope())
            .param("dataClass", request.dataClass().name())
            .param("sourceValueDigest", request.sourceValueDigest())
            .param("keyVersion", request.keyVersion())
            .param("mappingVersion", request.mappingVersion())
            .param("now", OffsetDateTime.now(clock))
            .update();
    }

    private OffsetDateTime expiresAt(VaultTokenRequest request) {
        if (request.ttl() == null) {
            return null;
        }
        return OffsetDateTime.now(clock).plus(request.ttl());
    }
}
