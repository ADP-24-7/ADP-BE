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

@Component
public class JdbcVaultTokenAdapter implements VaultTokenPort {

    private final JdbcClient jdbcClient;
    private final Clock clock;
    private final MeterRegistry meterRegistry;

    public JdbcVaultTokenAdapter(JdbcClient jdbcClient, Clock clock, MeterRegistry meterRegistry) {
        this.jdbcClient = jdbcClient;
        this.clock = clock;
        this.meterRegistry = meterRegistry;
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
            jdbcClient.sql("""
                    insert into vault.token_mapping (
                        token_ref, mapping_scope, data_class, source_value_digest, key_version,
                        mapping_version, expires_at, created_at
                    )
                    values (
                        :tokenRef, :mappingScope, :dataClass, :sourceValueDigest, :keyVersion,
                        :mappingVersion, :expiresAt, :createdAt
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
            meterRegistry.counter("vault.token.create.total", "result", "CREATED").increment();
            return tokenRef;
        } catch (DuplicateKeyException exception) {
            meterRegistry.counter("vault.token.create.total", "result", "DUPLICATE").increment();
            return find(request)
                .or(() -> replaceExpiredToken(request, tokenRef))
                .orElseThrow(() -> exception);
        }
    }

    private java.util.Optional<String> replaceExpiredToken(VaultTokenRequest request, String tokenRef) {
        int updated = jdbcClient.sql("""
                update vault.token_mapping
                set token_ref = :tokenRef,
                    expires_at = :expiresAt,
                    created_at = :createdAt
                where mapping_scope = :mappingScope
                  and data_class = :dataClass
                  and source_value_digest = :sourceValueDigest
                  and key_version = :keyVersion
                  and mapping_version = :mappingVersion
                  and expires_at <= :now
                """)
            .param("tokenRef", tokenRef)
            .param("expiresAt", expiresAt(request))
            .param("createdAt", OffsetDateTime.now(clock))
            .param("mappingScope", request.mappingScope())
            .param("dataClass", request.dataClass().name())
            .param("sourceValueDigest", request.sourceValueDigest())
            .param("keyVersion", request.keyVersion())
            .param("mappingVersion", request.mappingVersion())
            .param("now", OffsetDateTime.now(clock))
            .update();
        if (updated == 0) {
            return java.util.Optional.empty();
        }
        meterRegistry.counter("vault.token.create.total", "result", "REPLACED_EXPIRED").increment();
        return java.util.Optional.of(tokenRef);
    }

    private OffsetDateTime expiresAt(VaultTokenRequest request) {
        if (request.ttl() == null) {
            return null;
        }
        return OffsetDateTime.now(clock).plus(request.ttl());
    }
}
