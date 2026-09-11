package com.adp.gateway.ai.domain;

public record AiCalibrationFindingSource(
    String executionId,
    String findingType,
    String evidenceDigest,
    String sourceDataClass,
    String transformStrategy,
    String fieldTreatment,
    String outboundFieldPathDigest
) {
    public boolean missingReflectionMetadata() {
        return "RAW_VALUE_REFLECTION".equals(findingType)
            && (sourceDataClass == null || transformStrategy == null
                || fieldTreatment == null || outboundFieldPathDigest == null);
    }
}
