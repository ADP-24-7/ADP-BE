package com.adp.gateway.digitalasset.application;

import java.time.OffsetDateTime;
import java.util.Set;

import com.adp.gateway.digitalasset.domain.DigitalAssetOperationsOverview;

public interface DigitalAssetOperationsOverviewPort {
    DigitalAssetOperationsOverview load(
        String institutionId,
        Set<String> allowedWorkloads,
        OffsetDateTime from,
        OffsetDateTime to,
        OffsetDateTime generatedAt
    );
}
