package com.adp.gateway.ai.infrastructure;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import com.adp.gateway.ai.application.AiEvaluationContractPort;
import com.adp.gateway.ai.application.AiEvaluationRunMismatchException;
import com.adp.gateway.ai.domain.AiEvaluationContractSnapshot;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JdbcAiEvaluationContractAdapter implements AiEvaluationContractPort {
    private final JdbcClient jdbc;
    private final ObjectMapper mapper;
    public JdbcAiEvaluationContractAdapter(JdbcClient jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc; this.mapper = mapper;
    }
    @Override public Optional<AiEvaluationContractSnapshot> load(String runId) {
        return jdbc.sql("select snapshot_json from runtime.ai_evaluation_contract where evaluation_run_id = :run")
            .param("run", runId).query(String.class).optional().map(this::decode);
    }
    @Override @Transactional
    public AiEvaluationContractSnapshot freeze(String runId, AiEvaluationContractSnapshot snapshot) {
        jdbc.sql("""
            insert into runtime.ai_evaluation_contract(evaluation_run_id, fixed_conditions_digest, snapshot_json)
            values (:run, :digest, :snapshot) on conflict (evaluation_run_id) do nothing
            """).param("run", runId).param("digest", snapshot.fixedConditionsDigest())
            .param("snapshot", encode(snapshot)).update();
        var stored = load(runId).orElseThrow();
        if (!stored.equals(snapshot)) throw new AiEvaluationRunMismatchException("AI_FIXED_CONDITIONS_MISMATCH");
        return stored;
    }
    @Override @Transactional public void bind(String executionId, String runId, String caseId, String digest,
        String modelProfileDigest, String decisionId, String transformExecutionId,
        String outboundPayloadId, String providerRequestDigest, String providerInputDigest) {
        jdbc.sql("""
            insert into runtime.ai_evaluation_case_input(evaluation_run_id, eval_case_id, provider_input_digest)
            values (:run, :case, :input) on conflict (evaluation_run_id, eval_case_id) do nothing
            """).param("run", runId).param("case", caseId).param("input", providerInputDigest).update();
        String pinned = jdbc.sql("""
            select provider_input_digest from runtime.ai_evaluation_case_input
            where evaluation_run_id = :run and eval_case_id = :case
            """).param("run", runId).param("case", caseId).query(String.class).single();
        if (!pinned.equals(providerInputDigest)) throw new AiEvaluationRunMismatchException("AI_PROVIDER_INPUT_MISMATCH");
        jdbc.sql("""
            insert into runtime.ai_evaluation_contract_binding
              (execution_id, evaluation_run_id, eval_case_id, fixed_conditions_digest, model_profile_digest,
               decision_id, transform_execution_id, outbound_payload_id, provider_request_digest, provider_input_digest)
            values (:execution, :run, :case, :digest, :model, :decision, :transform, :outbound, :request, :input)
            """).param("execution", executionId).param("run", runId).param("case", caseId)
            .param("digest", digest).param("model", modelProfileDigest).param("decision", decisionId)
            .param("transform", transformExecutionId).param("outbound", outboundPayloadId)
            .param("request", providerRequestDigest).param("input", providerInputDigest).update();
    }
    @Override public Map<String, Object> evidence(String runId, List<String> executionIds) {
        var snapshot = load(runId).orElseThrow(() -> new AiEvaluationRunMismatchException("AI_CONTRACT_NOT_FROZEN"));
        var rows = jdbc.sql("""
            select b.execution_id, b.evaluation_run_id, b.eval_case_id, b.fixed_conditions_digest, b.model_profile_digest,
                   b.decision_id, b.transform_execution_id, b.outbound_payload_id, b.provider_request_digest,
                   b.provider_input_digest, r.outbound_guard_status
            from runtime.ai_evaluation_contract_binding b
            join runtime.ai_model_execution_evidence e on e.execution_id = b.execution_id
            join runtime.runtime_execution r on r.execution_id = b.execution_id
            where b.evaluation_run_id = :run and b.execution_id in (:ids)
              and e.evaluation_run_id = b.evaluation_run_id and e.eval_case_id = b.eval_case_id
              and e.profile_digest = b.model_profile_digest and r.decision_id = b.decision_id
              and r.transform_execution_id = b.transform_execution_id
              and r.outbound_payload_id = b.outbound_payload_id
              and r.outbound_guard_status = 'PASSED'
              and r.provider_request_digest = b.provider_request_digest
            order by b.execution_id
            """).param("run", runId).param("ids", executionIds).query().listOfRows();
        if (rows.size() != executionIds.size()) throw new AiEvaluationRunMismatchException("AI_CONTRACT_BINDING_MISSING");
        return Map.of("snapshot", snapshot, "bindings", rows);
    }
    private String encode(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (Exception e) { throw new IllegalStateException("Cannot serialize contract", e); }
    }
    private AiEvaluationContractSnapshot decode(String value) {
        try { return mapper.readValue(value, AiEvaluationContractSnapshot.class); }
        catch (Exception e) { throw new IllegalStateException("Invalid stored contract", e); }
    }
}
