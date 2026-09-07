package com.adp.gateway.ai.infrastructure;

import java.time.Clock;
import java.time.OffsetDateTime;

import com.adp.gateway.ai.application.AiModelExecutionEvidencePort;
import com.adp.gateway.ai.domain.AiModelProfile;
import com.adp.gateway.ai.domain.AiEvaluationRunDefinition;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class JdbcAiModelExecutionEvidenceAdapter implements AiModelExecutionEvidencePort {

    private final JdbcClient jdbcClient;
    private final Clock clock;

    public JdbcAiModelExecutionEvidenceAdapter(JdbcClient jdbcClient, Clock clock) {
        this.jdbcClient = jdbcClient;
        this.clock = clock;
    }

    @Override
    public void record(
        String executionId,
        AiModelProfile profile,
        AiEvaluationRunDefinition evaluationRun,
        String evalCaseId,
        String destinationProfileDigest
    ) {
        jdbcClient.sql("""
            insert into runtime.ai_model_execution_evidence (
                execution_id, profile_id, profile_version, profile_digest,
                provider_model_id, provider_model_version, connection_profile_id,
                max_tokens, temperature, sampling_profile_version,
                evaluation_run_id, evaluation_run_version, eval_case_id,
                dataset_id, dataset_version, dataset_digest,
                policy_snapshot_digest, destination_profile_digest, recorded_at
            ) values (
                :executionId, :profileId, :profileVersion, :profileDigest,
                :providerModelId, :providerModelVersion, :connectionProfileId,
                :maxTokens, :temperature, :samplingProfileVersion,
                :evaluationRunId, :evaluationRunVersion, :evalCaseId,
                :datasetId, :datasetVersion, :datasetDigest,
                :policySnapshotDigest, :destinationProfileDigest, :recordedAt
            )
            """)
            .param("executionId", executionId)
            .param("profileId", profile.profileId())
            .param("profileVersion", profile.profileVersion())
            .param("profileDigest", profile.modelProfileDigest())
            .param("providerModelId", profile.modelId())
            .param("providerModelVersion", profile.modelVersion())
            .param("connectionProfileId", profile.providerConnectionProfileId())
            .param("maxTokens", profile.maxTokens())
            .param("temperature", profile.temperature())
            .param("samplingProfileVersion", profile.profileVersion())
            .param("evaluationRunId", evaluationRun == null ? null : evaluationRun.evaluationRunId())
            .param("evaluationRunVersion", evaluationRun == null ? null : evaluationRun.runVersion())
            .param("evalCaseId", evalCaseId)
            .param("datasetId", evaluationRun == null ? null : evaluationRun.datasetId())
            .param("datasetVersion", evaluationRun == null ? null : evaluationRun.datasetVersion())
            .param("datasetDigest", evaluationRun == null ? null : evaluationRun.datasetDigest())
            .param("policySnapshotDigest", evaluationRun == null ? null : evaluationRun.policySnapshotDigest())
            .param("destinationProfileDigest", evaluationRun == null ? null : destinationProfileDigest)
            .param("recordedAt", OffsetDateTime.now(clock))
            .update();
    }
}
