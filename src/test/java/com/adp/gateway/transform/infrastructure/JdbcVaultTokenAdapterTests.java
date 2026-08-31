package com.adp.gateway.transform.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.adp.gateway.retrieval.domain.DataClass;
import com.adp.gateway.transform.application.VaultTokenRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

@SpringBootTest
class JdbcVaultTokenAdapterTests {

    @Autowired
    private JdbcVaultTokenAdapter adapter;

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void sameScopeAndDigestReusesToken() {
        String suffix = UUID.randomUUID().toString();
        VaultTokenRequest request = request("scope_same_" + suffix, "digest_same_" + suffix);

        String first = adapter.tokenFor(request);
        String second = adapter.tokenFor(request);

        assertThat(second).isEqualTo(first);
    }

    @Test
    void differentScopeCreatesSeparateToken() {
        String suffix = UUID.randomUUID().toString();
        String sourceDigest = "digest_scope_" + suffix;

        String first = adapter.tokenFor(request("scope_a_" + suffix, sourceDigest));
        String second = adapter.tokenFor(request("scope_b_" + suffix, sourceDigest));

        assertThat(second).isNotEqualTo(first);
    }

    @Test
    void expiredTokenIsNotReused() {
        String suffix = UUID.randomUUID().toString();
        String scope = "scope_expired_" + suffix;
        String sourceDigest = "digest_expired_" + suffix;
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
            .param("tokenRef", "vault_tok_expired_" + suffix)
            .param("mappingScope", scope)
            .param("dataClass", DataClass.CUSTOMER_IDENTIFIER.name())
            .param("sourceValueDigest", sourceDigest)
            .param("keyVersion", "key-v1")
            .param("mappingVersion", "mapping-v1")
            .param("expiresAt", OffsetDateTime.now().minusMinutes(1))
            .param("createdAt", OffsetDateTime.now().minusHours(2))
            .update();

        String token = adapter.tokenFor(request(scope, sourceDigest));

        assertThat(token).isNotEqualTo("vault_tok_expired_" + suffix);
    }

    private VaultTokenRequest request(String scope, String sourceDigest) {
        return new VaultTokenRequest(
            scope,
            DataClass.CUSTOMER_IDENTIFIER,
            sourceDigest,
            "key-v1",
            "mapping-v1",
            Duration.ofHours(1)
        );
    }
}
