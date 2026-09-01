package com.adp.gateway.egress.domain;

import java.util.Set;

public record DestinationProfile(
    String destinationProfileId,
    String providerProfileId,
    ExecutionPackType packType,
    String schemaVersion,
    boolean enabled,
    Set<String> allowedWorkloads,
    Set<String> allowedPurposes
) {

    public DestinationProfile {
        allowedWorkloads = Set.copyOf(allowedWorkloads);
        allowedPurposes = Set.copyOf(allowedPurposes);
    }

    public boolean allows(String workloadId, String purposeCode) {
        return enabled
            && allowedWorkloads.contains(workloadId)
            && allowedPurposes.contains(purposeCode);
    }
}
