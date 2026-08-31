package com.adp.gateway.transform.infrastructure;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.adp.gateway.retrieval.domain.DataClass;
import com.adp.gateway.transform.application.VaultTokenPort;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class JdbcVaultTokenAdapter implements VaultTokenPort {

    private final JdbcClient jdbcClient;
    private final Clock clock;

    public JdbcVaultTokenAdapter(JdbcClient jdbcClient, Clock clock) {
        this.jdbcClient = jdbcClient;
        this.clock = clock;
    }

    @Override
    public String tokenFor(String scope, DataClass dataClass, String sourceValueDigest) {
        return find(scope, dataClass, sourceValueDigest)
            .orElseGet(() -> create(scope, dataClass, sourceValueDigest));
    }

    private java.util.Optional<String> find(String scope, DataClass dataClass, String sourceValueDigest) {
        return jdbcClient.sql("""
                select token_ref
                from vault.token_mapping
                where mapping_scope = :scope
                  and data_class = :dataClass
                  and source_value_digest = :sourceValueDigest
                """)
            .param("scope", scope)
            .param("dataClass", dataClass.name())
            .param("sourceValueDigest", sourceValueDigest)
            .query(String.class)
            .optional();
    }

    private String create(String scope, DataClass dataClass, String sourceValueDigest) {
        String tokenRef = "vault_tok_" + UUID.randomUUID();
        try {
            jdbcClient.sql("""
                    insert into vault.token_mapping (
                        token_ref, mapping_scope, data_class, source_value_digest, key_version,
                        mapping_version, created_at
                    )
                    values (
                        :tokenRef, :scope, :dataClass, :sourceValueDigest, :keyVersion,
                        :mappingVersion, :createdAt
                    )
                    """)
                .param("tokenRef", tokenRef)
                .param("scope", scope)
                .param("dataClass", dataClass.name())
                .param("sourceValueDigest", sourceValueDigest)
                .param("keyVersion", "project-provisional-key-v1")
                .param("mappingVersion", "project-provisional-mapping-v1")
                .param("createdAt", OffsetDateTime.now(clock))
                .update();
            return tokenRef;
        } catch (DuplicateKeyException exception) {
            return find(scope, dataClass, sourceValueDigest)
                .orElseThrow(() -> exception);
        }
    }
}
