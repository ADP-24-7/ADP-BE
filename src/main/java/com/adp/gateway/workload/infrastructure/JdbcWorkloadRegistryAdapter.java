package com.adp.gateway.workload.infrastructure;

import java.util.Optional;

import com.adp.gateway.workload.domain.WorkloadDefinition;
import com.adp.gateway.workload.domain.WorkloadRegistryPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class JdbcWorkloadRegistryAdapter implements WorkloadRegistryPort {

    private final JdbcClient jdbcClient;

    public JdbcWorkloadRegistryAdapter(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public Optional<WorkloadDefinition> findEnabled(String workloadId) {
        return jdbcClient.sql("""
                select workload_id, display_name, description, enabled
                from workload_registry
                where workload_id = :workloadId
                  and enabled = true
                """)
            .param("workloadId", workloadId)
            .query((rs, rowNum) -> new WorkloadDefinition(
                rs.getString("workload_id"),
                rs.getString("display_name"),
                rs.getString("description"),
                rs.getBoolean("enabled")
            ))
            .optional();
    }
}
