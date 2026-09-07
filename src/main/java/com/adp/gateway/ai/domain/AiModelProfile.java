package com.adp.gateway.ai.domain;

public record AiModelProfile(
    String profileId,
    String profileVersion,
    String modelId,
    String modelVersion,
    String providerConnectionProfileId,
    String modelProfileDigest,
    String destinationProfileId,
    String destinationProfileVersion,
    String destinationProfileDigest,
    int maxTokens,
    double temperature
) {

    public AiModelProfile {
        if (profileId == null || profileId.isBlank() || modelId == null || modelId.isBlank()
            || providerConnectionProfileId == null || providerConnectionProfileId.isBlank()
            || modelProfileDigest == null || !modelProfileDigest.startsWith("sha256:")) {
            throw new IllegalArgumentException("AI model profile identity and provenance are required");
        }
        if (maxTokens <= 0 || temperature < 0.0) {
            throw new IllegalArgumentException("AI model sampling parameters are invalid");
        }
    }
}
