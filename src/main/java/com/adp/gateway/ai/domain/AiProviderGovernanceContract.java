package com.adp.gateway.ai.domain;

import java.util.List;

public record AiProviderGovernanceContract(
    String contractVersion,
    String contractDigest,
    String activationStatus,
    String providerConnectionProfileId,
    List<String> modelProfileIds,
    String workloadId,
    String purposeCode,
    List<String> destinationProfileIds,
    List<String> destinationProfileDigests,
    List<String> allowedRegions,
    boolean regionRequired,
    String approvedRetentionMode,
    Integer maximumRetentionDays,
    List<String> allowedReusePurposes
) {
    public AiProviderGovernanceContract {
        modelProfileIds = List.copyOf(modelProfileIds);
        destinationProfileIds = List.copyOf(destinationProfileIds);
        destinationProfileDigests = List.copyOf(destinationProfileDigests);
        allowedRegions = List.copyOf(allowedRegions);
        allowedReusePurposes = List.copyOf(allowedReusePurposes);
    }
}
