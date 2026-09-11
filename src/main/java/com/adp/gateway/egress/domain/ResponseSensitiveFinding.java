package com.adp.gateway.egress.domain;

public record ResponseSensitiveFinding(
    String findingType,
    String location,
    int startOffset,
    int endOffset,
    String detectorVersion,
    String evidenceDigest,
    String sourceDataClass,
    String transformStrategy,
    String fieldTreatment,
    String outboundFieldPathDigest
) {

    public ResponseSensitiveFinding(
        String findingType,
        String location,
        int startOffset,
        int endOffset,
        String detectorVersion,
        String evidenceDigest
    ) {
        this(findingType, location, startOffset, endOffset, detectorVersion, evidenceDigest,
            null, null, null, null);
    }
}
