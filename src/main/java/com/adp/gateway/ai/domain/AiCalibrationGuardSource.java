package com.adp.gateway.ai.domain;

public record AiCalibrationGuardSource(
    String executionId,
    String responseGuardStatus,
    String controlledDeliveryStatus,
    String reasonCodes,
    String detectorVersion,
    int findingCount
) { }
