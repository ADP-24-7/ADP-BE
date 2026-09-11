package com.adp.gateway.ai.infrastructure;

import java.util.List;
import java.util.Set;

import com.adp.gateway.ai.application.AiCalibrationEvidencePort;
import com.adp.gateway.ai.domain.AiCalibrationFindingSource;
import com.adp.gateway.ai.domain.AiCalibrationGuardSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class JdbcAiCalibrationEvidenceAdapter implements AiCalibrationEvidencePort {
    private final JdbcClient jdbcClient;

    public JdbcAiCalibrationEvidenceAdapter(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public List<AiCalibrationGuardSource> loadGuards(
        Set<String> executionIds,
        String institutionId,
        Set<String> allowedWorkloads
    ) {
        if (executionIds.isEmpty()) return List.of();
        String scope = workloadScope(allowedWorkloads);
        JdbcClient.StatementSpec statement = jdbcClient.sql("""
            select re.execution_id, re.response_guard_status, re.controlled_delivery_status,
                   rg.reason_codes, rg.detector_version, rg.finding_count
            from runtime.runtime_execution re
            join runtime.response_guard_result rg on rg.execution_id = re.execution_id
            where re.execution_id in (:executionIds)
              and re.institution_id = :institutionId
            """ + scope + " order by re.execution_id")
            .param("executionIds", executionIds)
            .param("institutionId", institutionId);
        statement = bindWorkloads(statement, allowedWorkloads);
        return statement.query(AiCalibrationGuardSource.class).list();
    }

    @Override
    public List<AiCalibrationFindingSource> loadFindings(
        Set<String> executionIds,
        String institutionId,
        Set<String> allowedWorkloads
    ) {
        if (executionIds.isEmpty()) return List.of();
        String scope = workloadScope(allowedWorkloads);
        JdbcClient.StatementSpec statement = jdbcClient.sql("""
            select sf.execution_id, sf.finding_type, sf.evidence_digest,
                   sf.source_data_class, sf.transform_strategy, sf.field_treatment,
                   sf.outbound_field_path_digest
            from runtime.response_sensitive_finding sf
            join runtime.runtime_execution re on re.execution_id = sf.execution_id
            where sf.execution_id in (:executionIds)
              and re.institution_id = :institutionId
            """ + scope + " order by sf.execution_id, sf.finding_type, sf.id")
            .param("executionIds", executionIds)
            .param("institutionId", institutionId);
        statement = bindWorkloads(statement, allowedWorkloads);
        return statement.query(AiCalibrationFindingSource.class).list();
    }

    private String workloadScope(Set<String> allowedWorkloads) {
        if (allowedWorkloads.contains("*")) return "";
        return allowedWorkloads.isEmpty() ? " and 1 = 0" : " and re.workload_id in (:allowedWorkloads)";
    }

    private JdbcClient.StatementSpec bindWorkloads(
        JdbcClient.StatementSpec statement,
        Set<String> allowedWorkloads
    ) {
        if (!allowedWorkloads.contains("*") && !allowedWorkloads.isEmpty()) {
            return statement.param("allowedWorkloads", allowedWorkloads);
        }
        return statement;
    }
}
