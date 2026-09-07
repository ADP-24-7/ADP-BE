package com.adp.gateway.ai.application;

import java.util.Optional;

import com.adp.gateway.ai.domain.AiProviderConnection;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AiProviderConnectionRegistry {

    private final AiModelProfileCatalog modelProfiles;
    private final String internalBaseUrl;
    private final String nvidiaBaseUrl;
    private final String nvidiaApiKey;

    public AiProviderConnectionRegistry(
        AiModelProfileCatalog modelProfiles,
        @Value("${adp.ai-connector.base-url:http://localhost:8090}") String internalBaseUrl,
        @Value("${adp.ai-connector.nvidia-base-url:https://integrate.api.nvidia.com}") String nvidiaBaseUrl,
        @Value("${adp.ai-connector.api-key:}") String nvidiaApiKey
    ) {
        this.modelProfiles = modelProfiles;
        this.internalBaseUrl = internalBaseUrl;
        this.nvidiaBaseUrl = nvidiaBaseUrl;
        this.nvidiaApiKey = nvidiaApiKey == null ? "" : nvidiaApiKey.trim();
    }

    public Optional<AiProviderConnection> resolve(String providerProfileId) {
        if ("internal-provider".equals(providerProfileId)) {
            return Optional.of(new AiProviderConnection(
                "internal-ai", internalBaseUrl, AiProviderConnection.CredentialType.NONE, ""
            ));
        }
        return modelProfiles.findByProfileId(providerProfileId).map(profile -> new AiProviderConnection(
            profile.providerConnectionProfileId(),
            nvidiaBaseUrl,
            AiProviderConnection.CredentialType.NVIDIA_API_KEY,
            nvidiaApiKey
        ));
    }
}
