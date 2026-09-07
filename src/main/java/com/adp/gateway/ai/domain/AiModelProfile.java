package com.adp.gateway.ai.domain;

public record AiModelProfile(
    String profileId,
    String profileVersion,
    String modelId,
    String modelVersion,
    String destinationProfileId,
    String destinationProfileVersion,
    String destinationProfileDigest,
    int maxTokens,
    double temperature
) {
}

