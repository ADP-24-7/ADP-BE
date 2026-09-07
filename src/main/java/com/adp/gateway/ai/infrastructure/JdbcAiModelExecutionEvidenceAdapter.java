package com.adp.gateway.ai.infrastructure;

import java.time.Clock;
import java.time.OffsetDateTime;

import com.adp.gateway.ai.application.AiModelExecutionEvidencePort;
import com.adp.gateway.ai.domain.AiModelProfile;
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
    public void record(String executionId, AiModelProfile profile) {
        jdbcClient.sql("""
            insert into runtime.ai_model_execution_evidence (
                execution_id, profile_id, profile_version, profile_digest,
                provider_model_id, provider_model_version, connection_profile_id,
                max_tokens, temperature, sampling_profile_version, recorded_at
            ) values (
                :executionId, :profileId, :profileVersion, :profileDigest,
                :providerModelId, :providerModelVersion, :connectionProfileId,
                :maxTokens, :temperature, :samplingProfileVersion, :recordedAt
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
            .param("recordedAt", OffsetDateTime.now(clock))
            .update();
    }
}
