package com.adp.gateway.egress.domain;

public record ResponseSensitiveFinding(
    String findingType,
    String location,
    int startOffset,
    int endOffset,
    String detectorVersion,
    String evidenceDigest
) {
}
