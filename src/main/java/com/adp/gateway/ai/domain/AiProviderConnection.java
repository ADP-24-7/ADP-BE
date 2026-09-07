package com.adp.gateway.ai.domain;

public record AiProviderConnection(
    String connectionProfileId,
    String baseUrl,
    CredentialType credentialType,
    String credential
) {

    public enum CredentialType {
        NONE,
        NVIDIA_API_KEY
    }

    @Override
    public String toString() {
        return "AiProviderConnection[connectionProfileId=" + connectionProfileId
            + ", baseUrl=" + baseUrl + ", credentialType=" + credentialType + "]";
    }
}
