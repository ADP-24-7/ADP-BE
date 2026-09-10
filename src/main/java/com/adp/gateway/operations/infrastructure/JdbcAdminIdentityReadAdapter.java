package com.adp.gateway.operations.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import com.adp.gateway.auth.domain.AdpRole;
import com.adp.gateway.auth.domain.PrincipalType;
import com.adp.gateway.operations.application.AdminIdentityNotFoundException;
import com.adp.gateway.operations.application.AdminIdentityReadPort;
import com.adp.gateway.operations.domain.AdminIdentityDetail;
import com.adp.gateway.operations.domain.AdminIdentityItem;
import com.adp.gateway.operations.domain.AdminIdentityPage;
import com.adp.gateway.operations.domain.AdminIdentityPermission;
import com.adp.gateway.operations.domain.WorkloadRegistryStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class JdbcAdminIdentityReadAdapter implements AdminIdentityReadPort {
    private static final String DELIMITER = "\u001f";

    private final JdbcClient jdbcClient;

    public JdbcAdminIdentityReadAdapter(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public AdminIdentityPage search(
        String institutionId,
        Set<String> allowedWorkloads,
        PrincipalType principalType,
        AdpRole role,
        String workloadId,
        Boolean enabled,
        String query,
        int page,
        int size
    ) {
        StringBuilder where = new StringBuilder(" where p.institution_id = :institutionId");
        appendPrincipalWorkloadScope(where, allowedWorkloads);
        if (principalType != null) where.append(" and p.principal_type = :principalType");
        if (role != null) {
            where.append(" and exists (select 1 from auth_principal_role rf where rf.principal_id = p.principal_id and rf.role_name = :role)");
        }
        if (workloadId != null) {
            where.append(" and exists (select 1 from auth_principal_workload wf where wf.principal_id = p.principal_id and wf.workload_id in (:workloadId, '*'))");
        }
        if (enabled != null) where.append(" and p.enabled = :enabled");
        if (query != null) {
            where.append(" and (lower(p.principal_id) like lower(:query) or lower(p.display_name) like lower(:query))");
        }

        JdbcClient.StatementSpec select = bind(jdbcClient.sql("""
            select p.principal_id, p.principal_type, p.display_name, p.institution_id,
                   p.subject_authorization_required, p.enabled, p.created_at,
                   coalesce((
                       select string_agg(r.role_name, chr(31) order by r.role_name)
                       from auth_principal_role r
                       where r.principal_id = p.principal_id
                   ), '') as roles,
                   coalesce((
                       select string_agg(w.workload_id, chr(31) order by w.workload_id)
                       from auth_principal_workload w
                       where w.principal_id = p.principal_id
            """ + visibleWorkloadClause(allowedWorkloads, "w.workload_id") + """
                   ), '') as workload_ids,
                   (select count(*) from auth_api_key k where k.principal_id = p.principal_id and k.enabled) as enabled_api_keys,
                   (select count(*) from auth_api_key k where k.principal_id = p.principal_id) as total_api_keys
            from auth_principal p
            """ + where + " order by p.display_name, p.principal_id limit :size offset :offset"),
            institutionId, allowedWorkloads, principalType, role, workloadId, enabled, query)
            .param("size", size)
            .param("offset", page * size);
        List<AdminIdentityItem> items = select.query(this::identity).list();

        long total = bind(jdbcClient.sql("select count(*) from auth_principal p" + where),
            institutionId, allowedWorkloads, principalType, role, workloadId, enabled, query)
            .query(Long.class)
            .single();
        return new AdminIdentityPage(items, page, size, total);
    }

    @Override
    public AdminIdentityDetail load(String principalId, String institutionId, Set<String> allowedWorkloads) {
        JdbcClient.StatementSpec identityStatement = jdbcClient.sql("""
            select p.principal_id, p.principal_type, p.display_name, p.institution_id,
                   p.subject_authorization_required, p.enabled, p.created_at,
                   coalesce((
                       select string_agg(r.role_name, chr(31) order by r.role_name)
                       from auth_principal_role r
                       where r.principal_id = p.principal_id
                   ), '') as roles,
                   coalesce((
                       select string_agg(w.workload_id, chr(31) order by w.workload_id)
                       from auth_principal_workload w
                       where w.principal_id = p.principal_id
            """ + visibleWorkloadClause(allowedWorkloads, "w.workload_id") + """
                   ), '') as workload_ids,
                   (select count(*) from auth_api_key k where k.principal_id = p.principal_id and k.enabled) as enabled_api_keys,
                   (select count(*) from auth_api_key k where k.principal_id = p.principal_id) as total_api_keys
            from auth_principal p
            where p.principal_id = :principalId
              and p.institution_id = :institutionId
            """ + principalWorkloadScope(allowedWorkloads) + """
            """)
            .param("principalId", principalId)
            .param("institutionId", institutionId);
        identityStatement = bindAllowedWorkloads(identityStatement, allowedWorkloads);
        AdminIdentityItem identity = identityStatement.query(this::identity).optional()
            .orElseThrow(() -> new AdminIdentityNotFoundException(principalId));

        JdbcClient.StatementSpec permissionStatement = jdbcClient.sql("""
            select g.workload_id, coalesce(w.display_name, g.workload_id) as workload_name,
                   case
                       when w.workload_id is null then 'UNRESOLVED'
                       when w.enabled then 'ENABLED'
                       else 'DISABLED'
                   end as workload_registry_status,
                   g.action_name, g.purpose,
                   g.subject_type, count(distinct g.subject_id) as subject_grant_count
            from auth_subject_grant g
            left join workload_registry w on w.workload_id = g.workload_id
            where g.principal_id = :principalId
            """ + visibleWorkloadClause(allowedWorkloads, "g.workload_id") + """
            group by g.workload_id, w.workload_id, w.display_name, w.enabled,
                     g.action_name, g.purpose, g.subject_type
            order by g.workload_id, g.action_name, g.purpose, g.subject_type
            """)
            .param("principalId", principalId);
        permissionStatement = bindAllowedWorkloads(permissionStatement, allowedWorkloads);
        List<AdminIdentityPermission> permissions = permissionStatement.query((rs, rowNum) ->
            new AdminIdentityPermission(
                rs.getString("workload_id"), rs.getString("workload_name"),
                WorkloadRegistryStatus.valueOf(rs.getString("workload_registry_status")),
                rs.getString("action_name"),
                rs.getString("purpose"), rs.getString("subject_type"),
                rs.getInt("subject_grant_count")
            )
        ).list();
        return new AdminIdentityDetail(identity, permissions);
    }

    private JdbcClient.StatementSpec bind(
        JdbcClient.StatementSpec statement,
        String institutionId,
        Set<String> allowedWorkloads,
        PrincipalType principalType,
        AdpRole role,
        String workloadId,
        Boolean enabled,
        String query
    ) {
        statement = statement.param("institutionId", institutionId);
        statement = bindAllowedWorkloads(statement, allowedWorkloads);
        if (principalType != null) statement = statement.param("principalType", principalType.name());
        if (role != null) statement = statement.param("role", role.name());
        if (workloadId != null) statement = statement.param("workloadId", workloadId);
        if (enabled != null) statement = statement.param("enabled", enabled);
        if (query != null) statement = statement.param("query", "%" + query + "%");
        return statement;
    }

    private String visibleWorkloadClause(Set<String> allowedWorkloads, String column) {
        if (allowedWorkloads.contains("*")) return "";
        return allowedWorkloads.isEmpty()
            ? " and 1 = 0\n"
            : " and (" + column + " = '*' or " + column + " in (:allowedWorkloads))\n";
    }

    private void appendPrincipalWorkloadScope(StringBuilder sql, Set<String> allowedWorkloads) {
        sql.append(principalWorkloadScope(allowedWorkloads));
    }

    private String principalWorkloadScope(Set<String> allowedWorkloads) {
        if (allowedWorkloads.contains("*")) return "";
        if (allowedWorkloads.isEmpty()) return " and 1 = 0";
        return """
             and exists (
                 select 1
                 from auth_principal_workload visibility
                 where visibility.principal_id = p.principal_id
                   and (visibility.workload_id = '*' or visibility.workload_id in (:allowedWorkloads))
             )
            """;
    }

    private JdbcClient.StatementSpec bindAllowedWorkloads(
        JdbcClient.StatementSpec statement,
        Set<String> allowedWorkloads
    ) {
        if (!allowedWorkloads.contains("*") && !allowedWorkloads.isEmpty()) {
            return statement.param("allowedWorkloads", allowedWorkloads);
        }
        return statement;
    }

    private AdminIdentityItem identity(ResultSet rs, int rowNum) throws SQLException {
        return new AdminIdentityItem(
            rs.getString("principal_id"), PrincipalType.valueOf(rs.getString("principal_type")),
            rs.getString("display_name"), rs.getString("institution_id"), rs.getBoolean("enabled"),
            rs.getBoolean("subject_authorization_required"),
            values(rs.getString("roles")).stream().map(AdpRole::valueOf).toList(),
            values(rs.getString("workload_ids")), rs.getInt("enabled_api_keys"),
            rs.getInt("total_api_keys"), rs.getObject("created_at", OffsetDateTime.class)
        );
    }

    private List<String> values(String value) {
        if (value == null || value.isBlank()) return List.of();
        return Arrays.asList(value.split(DELIMITER, -1));
    }
}
