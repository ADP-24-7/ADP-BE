package com.adp.gateway.ai.infrastructure;

import java.util.List;
import java.util.Set;

import com.adp.gateway.ai.application.AiEvaluationBundlePort;
import com.adp.gateway.ai.domain.AiEvaluationBundleSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class JdbcAiEvaluationBundleAdapter implements AiEvaluationBundlePort {
    private final JdbcClient jdbcClient;

    public JdbcAiEvaluationBundleAdapter(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public List<AiEvaluationBundleSource> load(
        String evaluationRunId,
        String institutionId,
        Set<String> allowedWorkloads,
        int limit
    ) {
        String workloadScope = allowedWorkloads.contains("*")
            ? ""
            : allowedWorkloads.isEmpty() ? " and 1 = 0" : " and re.workload_id in (:allowedWorkloads)";
        JdbcClient.StatementSpec statement = jdbcClient.sql("""
            select distinct on (evidence.eval_case_id, evidence.profile_id) re.execution_id,
                   evidence.evaluation_run_id, evidence.evaluation_run_version,
                   evidence.evaluation_contract_digest, evidence.eval_case_id,
                   evidence.expected_input_digest, evidence.actual_input_digest,
                   evidence.dataset_id, evidence.dataset_version, evidence.dataset_digest,
                   evidence.policy_snapshot_digest,
                   evidence.profile_id, evidence.profile_version, evidence.profile_digest,
                   evidence.provider_model_id, evidence.provider_model_version,
                   evidence.connection_profile_id, evidence.max_tokens,
                   evidence.temperature::double precision as temperature,
                   evidence.sampling_profile_version, evidence.destination_profile_digest,
                   re.status as runtime_status, re.final_action, re.response_guard_status,
                   re.controlled_delivery_status,
                   evidence.provider_status, evidence.error_category, evidence.evidence_status,
                   evidence.measurement_type,
                   evidence.full_response_latency_ms as full_response_latency_millis,
                   evidence.attempt_elapsed_ms as attempt_elapsed_millis,
                   evidence.initial_runtime_latency_ms as initial_runtime_latency_millis,
                   evidence.input_tokens, evidence.output_tokens, evidence.total_tokens,
                   evidence.token_usage_status, evidence.provider_http_status,
                   re.decision_id, re.connector_execution_id,
                   re.provider_request_digest, re.provider_response_digest,
                   re.created_at, re.updated_at
            from runtime.ai_model_execution_evidence evidence
            join runtime.runtime_execution re on re.execution_id = evidence.execution_id
            where evidence.evaluation_run_id = :evaluationRunId
              and re.institution_id = :institutionId
            """ + workloadScope + """
            order by evidence.eval_case_id, evidence.profile_id, re.created_at desc, re.execution_id desc
            limit :limit
            """)
            .param("evaluationRunId", evaluationRunId)
            .param("institutionId", institutionId)
            .param("limit", limit);
        if (!allowedWorkloads.contains("*") && !allowedWorkloads.isEmpty()) {
            statement = statement.param("allowedWorkloads", allowedWorkloads);
        }
        return statement.query(AiEvaluationBundleSource.class).list();
    }

    @Override
    public long countStored(
        String evaluationRunId,
        String institutionId,
        Set<String> allowedWorkloads
    ) {
        String workloadScope = allowedWorkloads.contains("*")
            ? ""
            : allowedWorkloads.isEmpty() ? " and 1 = 0" : " and re.workload_id in (:allowedWorkloads)";
        JdbcClient.StatementSpec statement = jdbcClient.sql("""
            select count(*)
            from runtime.ai_model_execution_evidence evidence
            join runtime.runtime_execution re on re.execution_id = evidence.execution_id
            where evidence.evaluation_run_id = :evaluationRunId
              and re.institution_id = :institutionId
            """ + workloadScope)
            .param("evaluationRunId", evaluationRunId)
            .param("institutionId", institutionId);
        if (!allowedWorkloads.contains("*") && !allowedWorkloads.isEmpty()) {
            statement = statement.param("allowedWorkloads", allowedWorkloads);
        }
        Long count = statement.query(Long.class).single();
        return count == null ? 0 : count;
    }
}
