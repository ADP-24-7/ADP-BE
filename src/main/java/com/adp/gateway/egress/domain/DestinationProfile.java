package com.adp.gateway.egress.domain;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public record DestinationProfile(
    String destinationProfileId,
    String profileVersion,
    String profileDigest,
    String contractVersion,
    String providerProfileId,
    ExecutionPackType packType,
    String schemaVersion,
    String status,
    OffsetDateTime effectiveAt,
    OffsetDateTime expiresAt,
    List<DestinationBinding> allowedBindings,
    List<DestinationFieldContract> fieldContracts
) {

    public DestinationProfile {
        allowedBindings = List.copyOf(allowedBindings);
        fieldContracts = List.copyOf(fieldContracts);
    }

    public boolean isEffectiveAt(OffsetDateTime requestStartedAt) {
        return "ACTIVE".equals(status)
            && !effectiveAt.isAfter(requestStartedAt)
            && (expiresAt == null || expiresAt.isAfter(requestStartedAt));
    }

    public boolean allows(String workloadId, String purposeCode, OffsetDateTime requestStartedAt) {
        return "ACTIVE".equals(status)
            && isEffectiveAt(requestStartedAt)
            && allowedBindings.stream()
                .anyMatch(binding -> binding.workloadId().equals(workloadId) && binding.purposeCode().equals(purposeCode));
    }

    public Optional<DestinationFieldContract> fieldContract(String path) {
        return fieldContracts.stream()
            .filter(contract -> contract.path().equals(path) || path.endsWith("." + contract.path()))
            .findFirst();
    }
}
