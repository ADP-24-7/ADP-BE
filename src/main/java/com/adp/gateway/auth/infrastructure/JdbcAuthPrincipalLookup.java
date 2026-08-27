package com.adp.gateway.auth.infrastructure;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import com.adp.gateway.auth.application.AuthPrincipalLookup;
import com.adp.gateway.auth.domain.AdpRole;
import com.adp.gateway.auth.domain.AuthPrincipal;
import com.adp.gateway.auth.domain.PrincipalType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class JdbcAuthPrincipalLookup implements AuthPrincipalLookup {

    private final JdbcClient jdbcClient;

    public JdbcAuthPrincipalLookup(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public Optional<AuthPrincipal> findByApiKeyHash(String apiKeyHash) {
        return jdbcClient.sql("""
                select p.principal_id, p.principal_type, p.display_name, p.subject_authorization_required
                from auth_api_key k
                join auth_principal p on p.principal_id = k.principal_id
                where k.key_hash = :apiKeyHash
                  and k.enabled = true
                  and p.enabled = true
                """)
            .param("apiKeyHash", apiKeyHash)
            .query((rs, rowNum) -> new AuthPrincipal(
                rs.getString("principal_id"),
                PrincipalType.valueOf(rs.getString("principal_type")),
                rs.getString("display_name"),
                rs.getBoolean("subject_authorization_required"),
                workloadIds(rs.getString("principal_id")),
                roles(rs.getString("principal_id"))
            ))
            .optional();
    }

    private Set<AdpRole> roles(String principalId) {
        return new HashSet<>(jdbcClient.sql("""
                select role_name
                from auth_principal_role
                where principal_id = :principalId
                """)
            .param("principalId", principalId)
            .query((rs, rowNum) -> AdpRole.valueOf(rs.getString("role_name")))
            .list());
    }

    private Set<String> workloadIds(String principalId) {
        return new HashSet<>(jdbcClient.sql("""
                select workload_id
                from auth_principal_workload
                where principal_id = :principalId
                """)
            .param("principalId", principalId)
            .query(String.class)
            .list());
    }
}
